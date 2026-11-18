package io.github.alirezaspec.familynight

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * WebView shell around the live Family Night site
 * (https://alireza-spec.github.io/family-night-site/).
 *
 * The site itself is unchanged and served from GitHub Pages, so site
 * updates flow into the app with no app update. The only native additions
 * are the [AppBridge] JavaScript bridge (the site's "پخش با فمیلی نایت"
 * button hands a direct video URL to [PlayerActivity]) and the intent://
 * handling that keeps the site's MX Player / VLC buttons working.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    /** File picker for `<input type="file">` (the site's subtitle upload). */
    private val filePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileUploadCallback
        fileUploadCallback = null
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val uris = mutableListOf<Uri>()
            data?.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri
            }
            data?.data?.let { uris += it }
            callback?.onReceiveValue(uris.toTypedArray())
        } else {
            callback?.onReceiveValue(null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webview)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            // The site and every video host it links to are https; never
            // allow cleartext inside the app.
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Default caching: the live site's updates reach the app on
            // reload, and pages stay fast on repeat visits.
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                // intent:// links (the site's MX Player / VLC buttons) must
                // leave the WebView and open the target app.
                if (url.startsWith("intent://")) {
                    val intent = try {
                        Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    } catch (_: Exception) {
                        // Malformed intent URL: ignore the tap.
                        return true
                    }
                    try {
                        startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        // Target app (e.g. MX Player) is not installed: try
                        // the Play Store fallback the intent carries, if any.
                        val fallback = intent.getStringExtra("browser_fallback_url")
                        if (!fallback.isNullOrBlank()) view.loadUrl(fallback)
                    }
                    return true
                }
                // Normal http(s) links stay inside the WebView.
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                // Fullscreen video from the site's own in-browser player.
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                val decor = window.decorView as ViewGroup
                decor.addView(
                    view,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                webView.visibility = View.GONE
            }

            override fun onHideCustomView() {
                customView?.let { (window.decorView as ViewGroup).removeView(it) }
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                webView.visibility = View.VISIBLE
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(
                        Intent.EXTRA_ALLOW_MULTIPLE,
                        fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                    )
                }
                filePicker.launch(Intent.createChooser(intent, "Choose a file"))
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                // The site needs no device permissions (camera, mic,
                // location); deny by default.
                request.deny()
            }
        }

        webView.addJavascriptInterface(AppBridge(this), "FamilyNightApp")

        if (savedInstanceState == null) {
            webView.loadUrl("https://alireza-spec.github.io/family-night-site/")
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    webView.webChromeClient?.onHideCustomView()
                    return
                }
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }
}
