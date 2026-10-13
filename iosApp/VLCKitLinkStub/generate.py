#!/usr/bin/env python3
"""Writes MobileVLCKit.tbd, the link-time stub of MobileVLCKit's own API.

MobileVLCKit also exports libc's clock_gettime and its private zlib, libpng, FFmpeg and other
copies. When it is on the link line, the linker (ld-prime) sometimes binds the app's calls to
those, non-deterministically: gRPC (Firestore) then got VLCKit's clock_gettime and the app
aborted at launch. The app links against this stub instead, which lists only the VLC* symbols
and classes; at run time dyld still loads the real framework (same install name, embedded by
the Pods script).

Run after `pod install` / `pod update` changes MobileVLCKit:
    python3 iosApp/VLCKitLinkStub/generate.py
"""
import os
import re
import subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
XCFRAMEWORK = os.path.join(HERE, "..", "Pods", "MobileVLCKit", "MobileVLCKit.xcframework")
SLICES = {
    "ios-arm64_armv7_armv7s": ["arm64-ios"],
    "ios-arm64_x86_64-simulator": ["arm64-ios-simulator", "x86_64-ios-simulator"],
}


def exports(binary, arch):
    out = subprocess.run(["nm", "-gUj", "-arch", arch, binary], capture_output=True, text=True, check=True).stdout
    symbols, classes = set(), set()
    for name in out.split():
        m = re.match(r"_OBJC_(?:METACLASS|CLASS)_\$_(VLC\w+)$", name)
        if m:
            classes.add(m.group(1))
        elif re.match(r"_k?VLC\w+$", name):
            symbols.add(name)
    return symbols, classes


def main():
    symbols, classes, targets = set(), set(), []
    install_name = None
    for slice_dir, slice_targets in SLICES.items():
        binary = os.path.join(XCFRAMEWORK, slice_dir, "MobileVLCKit.framework", "MobileVLCKit")
        install_name = install_name or subprocess.run(
            ["otool", "-D", "-arch", "arm64", binary], capture_output=True, text=True, check=True
        ).stdout.strip().splitlines()[-1]
        s, c = exports(binary, "arm64")
        symbols |= s
        classes |= c
        targets += slice_targets

    def flow(items):
        return "[ " + ", ".join(f"'{i}'" for i in sorted(items)) + " ]"

    text = (
        "--- !tapi-tbd\n"
        "tbd-version:     4\n"
        f"targets:         {flow(targets).replace(chr(39), '')}\n"
        f"install-name:    '{install_name}'\n"
        "exports:\n"
        f"  - targets:         {flow(targets).replace(chr(39), '')}\n"
        f"    symbols:         {flow(symbols)}\n"
        f"    objc-classes:    {flow(classes)}\n"
        "...\n"
    )
    with open(os.path.join(HERE, "MobileVLCKit.tbd"), "w") as f:
        f.write(text)
    print(f"{len(symbols)} symbols, {len(classes)} classes -> MobileVLCKit.tbd")


if __name__ == "__main__":
    main()
