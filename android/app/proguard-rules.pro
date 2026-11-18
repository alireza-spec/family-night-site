# ProGuard / R8 rules. Minification is currently off (see build.gradle.kts),
# but these rules keep the app correct if it is ever turned on.
# Keep the JavaScript bridge: its methods are called by name from the WebView.
-keepclassmembers class io.github.alirezaspec.familynight.AppBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
