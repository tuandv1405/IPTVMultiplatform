#!/usr/bin/env bash
# Builds a signed TSIPTV.ipa for Firebase App Distribution. Used by
# .github/workflows/ios-firebase-distribution.yml; it also runs on a Mac (setup: docs/ci-ios-firebase-distribution.md).
#
#   IOS_PROFILE=TSIPTV_AdHoc.mobileprovision IOS_CERT_P12=cert.p12 IOS_CERT_PASSWORD=... iosApp/ci/build-ipa.sh
#
#   IOS_PROFILE        provisioning profile for tss.t.tsiptv: Ad Hoc (export method release-testing) or
#                      Development (debugging). The method and certificate follow from the profile.
#   IOS_CERT_P12       certificate + private key of a certificate in that profile. It is imported into a
#                      temporary keychain that is deleted afterwards. Leave it empty to sign with an identity
#                      that is already in the login keychain.
#   IOS_CERT_PASSWORD  password of the .p12.
#   BUILD_NUMBER       CFBundleVersion (default: the project's CURRENT_PROJECT_VERSION).
#   OUT_DIR            output directory (default: build/ios-ipa). The .ipa path is written to $OUT_DIR/ipa-path.txt.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
IOS="$ROOT/iosApp"
OUT_DIR=${OUT_DIR:-$ROOT/build/ios-ipa}
DERIVED=${DERIVED_DATA:-$ROOT/build/ios-derived-data}
WORK=$(mktemp -d)
KEYCHAIN=""
INSTALLED_PROFILES=()
OLD_KEYCHAINS=()

cleanup() {
  if [ -n "$KEYCHAIN" ]; then
    security list-keychains -d user -s "${OLD_KEYCHAINS[@]}" || true
    security delete-keychain "$KEYCHAIN" || true
  fi
  for p in ${INSTALLED_PROFILES[@]+"${INSTALLED_PROFILES[@]}"}; do rm -f "$p"; done
  rm -rf "$WORK"
}
trap cleanup EXIT

die() { echo "::error::$*" >&2; exit 1; }

[ -f "${IOS_PROFILE:-}" ] || die "IOS_PROFILE must point to a .mobileprovision file"

# --- Provisioning profile -------------------------------------------------------------------------
security cms -D -i "$IOS_PROFILE" > "$WORK/profile.plist"
pval() { /usr/libexec/PlistBuddy -c "Print :$1" "$WORK/profile.plist" 2>/dev/null || true; }
PROFILE_UUID=$(pval UUID)
PROFILE_NAME=$(pval Name)
TEAM_ID=$(pval TeamIdentifier:0)
APP_ID=$(pval Entitlements:application-identifier)
BUNDLE_ID=${APP_ID#"$TEAM_ID".}
if [ "$(pval Entitlements:get-task-allow)" = "true" ]; then
  METHOD=debugging
elif [ "$(pval ProvisionsAllDevices)" = "true" ]; then
  METHOD=enterprise
else
  METHOD=release-testing
fi
echo "Profile: $PROFILE_NAME ($PROFILE_UUID), team $TEAM_ID, app $BUNDLE_ID, export method $METHOD"
[ "$BUNDLE_ID" = "tss.t.tsiptv" ] || die "The profile is for '$APP_ID', not tss.t.tsiptv"
EXPIRES=$(plutil -extract ExpirationDate raw -o - "$WORK/profile.plist")
if [ "$(date -ju -f '%Y-%m-%dT%H:%M:%SZ' "$EXPIRES" +%s)" -lt "$(date +%s)" ]; then
  die "The provisioning profile expired on $EXPIRES"
fi

# Xcode 16+ reads profiles from UserData; older tools from MobileDevice.
for dir in "$HOME/Library/Developer/Xcode/UserData/Provisioning Profiles" "$HOME/Library/MobileDevice/Provisioning Profiles"; do
  mkdir -p "$dir"
  target="$dir/$PROFILE_UUID.mobileprovision"
  if [ ! -f "$target" ]; then
    cp "$IOS_PROFILE" "$target"
    INSTALLED_PROFILES+=("$target")
  fi
done

# --- Signing identity -----------------------------------------------------------------------------
if [ -n "${IOS_CERT_P12:-}" ]; then
  [ -f "$IOS_CERT_P12" ] || die "IOS_CERT_P12 does not exist: $IOS_CERT_P12"
  KEYCHAIN="$WORK/signing.keychain-db"
  KEYCHAIN_PASSWORD=$(openssl rand -hex 24)
  while IFS= read -r k; do OLD_KEYCHAINS+=("$k"); done < <(security list-keychains -d user | sed -e 's/^ *"//' -e 's/"$//')
  security create-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN"
  security set-keychain-settings -lut 21600 "$KEYCHAIN"
  security unlock-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN"
  security import "$IOS_CERT_P12" -k "$KEYCHAIN" -P "${IOS_CERT_PASSWORD:-}" -f pkcs12 \
    -T /usr/bin/codesign -T /usr/bin/security >/dev/null
  security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$KEYCHAIN_PASSWORD" "$KEYCHAIN" >/dev/null
  security list-keychains -d user -s "$KEYCHAIN" "${OLD_KEYCHAINS[@]}"
fi

# Sign with the certificate that the profile actually contains (by SHA-1), so an old
# "iPhone Distribution" certificate works as well as "Apple Distribution".
IDENTITIES=$(security find-identity -v -p codesigning ${KEYCHAIN:+"$KEYCHAIN"})
IDENTITY=""
i=0
while cert=$(plutil -extract "DeveloperCertificates.$i" raw -o - "$WORK/profile.plist" 2>/dev/null); do
  sha=$(printf '%s' "$cert" | base64 -D | shasum -a 1 | awk '{print toupper($1)}')
  if grep -q "$sha" <<<"$IDENTITIES"; then IDENTITY=$sha; break; fi
  i=$((i + 1))
done
[ -n "$IDENTITY" ] || die "None of the profile's $i certificates has its private key in the keychain (is IOS_CERT_P12 the right certificate?)"
echo "Signing identity: $(grep "$IDENTITY" <<<"$IDENTITIES" | sed 's/^ *[0-9]*) //')"

# --- CocoaPods (MobileVLCKit) ---------------------------------------------------------------------
if ! cmp -s "$IOS/Podfile.lock" "$IOS/Pods/Manifest.lock"; then
  (cd "$IOS" && pod install)
fi

# --- Archive and export ---------------------------------------------------------------------------
BUILD_NUMBER=${BUILD_NUMBER:-$(grep -m1 'CURRENT_PROJECT_VERSION = ' "$IOS/iosApp.xcodeproj/project.pbxproj" | tr -dc '0-9')}
ARCHIVE="$OUT_DIR/TSIPTV.xcarchive"
rm -rf "$ARCHIVE" "$OUT_DIR/export"
mkdir -p "$OUT_DIR"

# The profile goes through TSIPTV_PROVISIONING_PROFILE (Configuration/Config.xcconfig) so that only
# the app target gets it: Pods and Swift packages refuse a provisioning profile.
xcodebuild archive \
  -workspace "$IOS/iosApp.xcworkspace" \
  -scheme iosApp \
  -configuration Release \
  -destination generic/platform=iOS \
  -archivePath "$ARCHIVE" \
  -derivedDataPath "$DERIVED" \
  -skipPackagePluginValidation \
  -skipMacroValidation \
  CODE_SIGN_STYLE=Manual \
  DEVELOPMENT_TEAM="$TEAM_ID" \
  CODE_SIGN_IDENTITY="$IDENTITY" \
  TSIPTV_PROVISIONING_PROFILE="$PROFILE_UUID" \
  CURRENT_PROJECT_VERSION="$BUILD_NUMBER" \
  ${KEYCHAIN:+OTHER_CODE_SIGN_FLAGS="--keychain $KEYCHAIN"}

cat > "$WORK/ExportOptions.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>method</key><string>$METHOD</string>
  <key>teamID</key><string>$TEAM_ID</string>
  <key>signingStyle</key><string>manual</string>
  <key>signingCertificate</key><string>$IDENTITY</string>
  <key>provisioningProfiles</key>
  <dict><key>$BUNDLE_ID</key><string>$PROFILE_UUID</string></dict>
  <key>thinning</key><string>&lt;none&gt;</string>
</dict>
</plist>
EOF

xcodebuild -exportArchive \
  -archivePath "$ARCHIVE" \
  -exportPath "$OUT_DIR/export" \
  -exportOptionsPlist "$WORK/ExportOptions.plist"

IPA=$(ls "$OUT_DIR"/export/*.ipa | head -n 1)
[ -f "$IPA" ] || die "xcodebuild did not produce an .ipa"
echo "$IPA" > "$OUT_DIR/ipa-path.txt"
echo "IPA: $IPA (build $BUILD_NUMBER, $METHOD)"
