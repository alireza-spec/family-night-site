package io.github.alirezaspec.familynight

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.StyledPlayerView
import java.security.MessageDigest

/**
 * Native video player (Media3 ExoPlayer) for the Family Night site's direct
 * video links.
 *
 * Browsers cannot decode the MKV / H.265 files that make up most of the
 * archive; ExoPlayer can, because it brings its own container/codec stack
 * and falls back to software decoding when the device has no hardware
 * H.265 decoder ([DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON]).
 *
 * Opened by [AppBridge] from the site's "پخش با فمیلی نایت" button with a
 * direct https URL (+ optional subtitle URL). The player is landscape-first
 * like the site's own fullscreen player, remembers the position per URL
 * and offers to resume, and on failure shows a Persian error panel with
 * retry and copy-link actions.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SUB_URL = "sub_url"

        /** Browser-like UA: the video CDN blocks non-browser User-Agents. */
        const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

        private const val RESUME_MIN_MS = 15_000L
        private const val RESUME_END_MARGIN_MS = 15_000L
        private const val SEEK_STEP_MS = 10_000L
    }

    private lateinit var playerView: StyledPlayerView
    private lateinit var errorPanel: View
    private var player: ExoPlayer? = null
    private var videoUrl: String = ""
    private var videoTitle: String = ""
    private var subUrl: String? = null
    private var resumeAsked = false
    private var resumePosition = 0L

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("fnplay", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        videoUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        videoTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        subUrl = intent.getStringExtra(EXTRA_SUB_URL)
        if (videoUrl.isBlank()) {
            finish()
            return
        }
        title = videoTitle

        playerView = findViewById(R.id.player_view)
        errorPanel = findViewById(R.id.error_panel)
        findViewById<Button>(R.id.btn_retry).setOnClickListener { retry() }
        findViewById<Button>(R.id.btn_copy_link).setOnClickListener { copyLink() }

        resumePosition = prefs.getLong(positionKey(videoUrl), 0L)

        // Back: save the position (onStop saves it too, but save explicitly
        // so it survives even if the process is killed right after) and
        // close the player.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                savePosition()
                finish()
            }
        })
    }

    override fun onStart() {
        super.onStart()
        if (videoUrl.isBlank()) return
        buildPlayer()
    }

    override fun onStop() {
        super.onStop()
        savePosition()
        releasePlayer()
    }

    private fun positionKey(url: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "pos:$hash"
    }

    private fun buildPlayer() {
        resumeAsked = false
        // EXTENSION_RENDERER_MODE_ON lets ExoPlayer fall back to software
        // decoding when the device has no hardware H.265 decoder — that is
        // what makes MKV/x265 files playable on older phones.
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        // DefaultHttpDataSource follows HTTP redirects by default (the video
        // hosts answer 302 to the real CDN node) and keeps no cookies.
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(BROWSER_UA)
            .setAllowCrossProtocolRedirects(true)

        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
        player = exoPlayer
        exoPlayer.seekBackIncrementMs = SEEK_STEP_MS
        exoPlayer.seekForwardIncrementMs = SEEK_STEP_MS

        val itemBuilder = MediaItem.Builder().setUri(videoUrl)
        subUrl?.let { sub ->
            val mime = if (sub.lowercase().endsWith(".vtt")) {
                MimeTypes.TEXT_VTT
            } else {
                MimeTypes.TEXT_SRT
            }
            val subtitle = MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub))
                .setMimeType(mime)
                .setLanguage("fa")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            itemBuilder.setSubtitleConfigurations(listOf(subtitle))
        }
        exoPlayer.setMediaItem(itemBuilder.build())

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    errorPanel.visibility = View.GONE
                    maybeOfferResume()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                errorPanel.visibility = View.VISIBLE
            }
        })

        playerView.player = exoPlayer
        // Do not auto-play yet: maybeOfferResume() starts playback after
        // the user chooses resume / start-over (or immediately when there
        // is nothing to resume).
        exoPlayer.playWhenReady = false
        exoPlayer.prepare()
    }

    private fun maybeOfferResume() {
        if (resumeAsked) return
        resumeAsked = true
        val exoPlayer = player ?: return
        val duration = exoPlayer.duration
        if (resumePosition > RESUME_MIN_MS &&
            duration > 0 &&
            resumePosition < duration - RESUME_END_MARGIN_MS
        ) {
            AlertDialog.Builder(this)
                .setTitle(R.string.resume_title)
                .setMessage(
                    getString(R.string.resume_message, formatTime(resumePosition))
                )
                .setPositiveButton(R.string.resume_continue) { _, _ ->
                    exoPlayer.seekTo(resumePosition)
                    exoPlayer.play()
                }
                .setNegativeButton(R.string.resume_restart) { _, _ ->
                    exoPlayer.seekTo(0)
                    exoPlayer.play()
                }
                .setCancelable(false)
                .show()
        } else {
            exoPlayer.play()
        }
    }

    private fun retry() {
        errorPanel.visibility = View.GONE
        player?.prepare()
        player?.play()
    }

    private fun copyLink() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Family Night link", videoUrl))
        Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show()
    }

    private fun savePosition() {
        val exoPlayer = player ?: return
        val position = exoPlayer.currentPosition
        val duration = exoPlayer.duration
        val key = positionKey(videoUrl)
        if (duration > 0 && position > duration - RESUME_END_MARGIN_MS) {
            // Played to (near) the end: forget the saved position.
            prefs.edit().remove(key).apply()
        } else if (position > 0) {
            prefs.edit().putLong(key, position).apply()
        }
    }

    private fun releasePlayer() {
        player?.let {
            playerView.player = null
            it.release()
        }
        player = null
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            "%d:%02d:%02d".format(h, m, s)
        } else {
            "%d:%02d".format(m, s)
        }
    }
}
