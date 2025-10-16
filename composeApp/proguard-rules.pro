# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep TrustAllHttpDataSource and related SSL components
-keep class tss.t.tsiptv.player.network.TrustAllHttpDataSource { *; }
-keep class tss.t.tsiptv.player.network.TrustAllHttpDataSource$Factory { *; }

# Keep SSL utility classes
-keep class tss.t.tsiptv.core.network.SSLTrustAllUtils { *; }
