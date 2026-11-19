package com.nova.player

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.nova.player.ui.PlayerScreen
import com.nova.player.ui.theme.NovaTheme

class PlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private var inPip by mutableStateOf(false)
    private var landscape = false
    private var autoRotated = false
    private val prefs by lazy { getSharedPreferences("resume", MODE_PRIVATE) }

    private val subtitlePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) addSubtitle(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideSystemBars()
        landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        player = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        if (!loadMedia()) {
            finish()
            return
        }

        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (!autoRotated && videoSize.width > 0 && videoSize.height > 0) {
                    autoRotated = true
                    landscape = videoSize.width >= videoSize.height
                    requestedOrientation = if (landscape) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    }
                }
            }
        })

        player.prepare()
        player.playWhenReady = true

        setContent {
            NovaTheme {
                PlayerScreen(
                    exo = player,
                    inPip = inPip,
                    onBack = { finish() },
                    onEnterPip = { enterPip() },
                    onRotate = { toggleOrientation() },
                    onPickSubtitle = {
                        subtitlePicker.launch(arrayOf("application/x-subrip", "text/vtt", "text/*", "application/octet-stream"))
                    },
                )
            }
        }
    }

    private fun loadMedia(): Boolean {
        val uris = intent.getStringArrayListExtra(EXTRA_URIS)
        val titles = intent.getStringArrayListExtra(EXTRA_TITLES)
        val data = intent.data
        val items = when {
            uris != null -> uris.mapIndexed { i, u -> mediaItem(Uri.parse(u), titles?.getOrNull(i)) }
            data != null -> listOf(mediaItem(data, null))
            else -> emptyList()
        }
        if (items.isEmpty()) return false
        val index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, items.lastIndex)
        val resume = prefs.getLong(items[index].mediaId, 0L)
        player.setMediaItems(items, index, if (resume > 3_000) resume - 2_000 else 0L)
        return true
    }

    private fun mediaItem(uri: Uri, title: String?): MediaItem =
        MediaItem.Builder()
            .setUri(uri)
            .setMediaId(uri.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title ?: uri.lastPathSegment ?: "Video")
                    .build()
            )
            .build()

    private fun addSubtitle(uri: Uri) {
        val item = player.currentMediaItem ?: return
        val name = (uri.lastPathSegment ?: "").lowercase()
        val mime = when {
            name.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            name.endsWith(".ass") || name.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val sub = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mime)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val index = player.currentMediaItemIndex
        val pos = player.currentPosition
        player.replaceMediaItem(index, item.buildUpon().setSubtitleConfigurations(listOf(sub)).build())
        player.seekTo(index, pos)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
    }

    private fun toggleOrientation() {
        landscape = !landscape
        requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < 26 ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) return
        val vs = player.videoSize
        val raw = if (vs.width > 0 && vs.height > 0) vs.width.toFloat() / vs.height else 16f / 9f
        val ratio = when {
            raw > 2.39f -> Rational(239, 100)
            raw < 0.42f -> Rational(42, 100)
            else -> Rational(vs.width.coerceAtLeast(16), vs.height.coerceAtLeast(9))
        }
        try {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio).build())
        } catch (_: Exception) {
        }
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun saveResume() {
        val item = player.currentMediaItem ?: return
        val duration = player.duration
        val pos = player.currentPosition
        val editor = prefs.edit()
        if (duration > 0 && pos > duration - 5_000) editor.remove(item.mediaId)
        else editor.putLong(item.mediaId, pos)
        editor.apply()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player.isPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        saveResume()
    }

    override fun onStop() {
        super.onStop()
        player.pause()
    }

    override fun onDestroy() {
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URIS = "uris"
        const val EXTRA_TITLES = "titles"
        const val EXTRA_INDEX = "index"
    }
}
