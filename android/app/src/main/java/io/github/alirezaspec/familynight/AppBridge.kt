package io.github.alirezaspec.familynight

import android.app.Activity
import android.content.Intent
import android.webkit.JavascriptInterface

/**
 * JavaScript bridge exposed to the Family Night site as `window.FamilyNightApp`.
 *
 * The site calls [play] from its "پخش با فمیلی نایت" button when it runs
 * inside this app; in a normal browser the object does not exist and the
 * site falls back to its own in-browser player, unchanged.
 *
 * Deliberately minimal and strict: only plain https URLs with no whitespace
 * are accepted, and the bridge never touches the network, storage, or any
 * other app capability.
 */
class AppBridge(private val activity: Activity) {

    /** A URL is acceptable only as a plain https link with no whitespace. */
    private fun isPlayableUrl(url: String): Boolean {
        if (url.isBlank()) return false
        if (!url.startsWith("https://")) return false
        if (url.any { it.isWhitespace() }) return false
        return true
    }

    private fun openPlayer(url: String, title: String, subUrl: String?) {
        // JavascriptInterface methods run on a WebView thread, not the UI
        // thread, so the Activity launch must be posted to the main thread.
        activity.runOnUiThread {
            val intent = Intent(activity, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_URL, url)
                putExtra(PlayerActivity.EXTRA_TITLE, title)
                subUrl?.let { putExtra(PlayerActivity.EXTRA_SUB_URL, it) }
            }
            activity.startActivity(intent)
        }
    }

    @JavascriptInterface
    fun play(url: String, title: String) {
        if (!isPlayableUrl(url)) return
        openPlayer(url, title, null)
    }

    @JavascriptInterface
    fun playWithSubs(url: String, title: String, subUrl: String) {
        if (!isPlayableUrl(url)) return
        // A subtitle is optional: forward it only when it is also a clean
        // https link; otherwise play without subtitles.
        val sub = if (isPlayableUrl(subUrl)) subUrl else null
        openPlayer(url, title, sub)
    }
}
