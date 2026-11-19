package com.nova.player.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nova.player.util.formatTime
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private enum class PlayerDialog { Speed, Audio, Subtitle }

private val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)
private val resizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch",
)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    exo: ExoPlayer,
    inPip: Boolean,
    onBack: () -> Unit,
    onEnterPip: () -> Unit,
    onRotate: () -> Unit,
    onPickSubtitle: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var isPlaying by remember { mutableStateOf(exo.isPlaying) }
    var buffering by remember { mutableStateOf(exo.playbackState == Player.STATE_BUFFERING) }
    var tracks by remember { mutableStateOf(exo.currentTracks) }
    var mediaTitle by remember { mutableStateOf(exo.mediaMetadata.title?.toString().orEmpty()) }
    var speed by remember { mutableFloatStateOf(exo.playbackParameters.speed) }
    var resizeIndex by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }
    var seekPreview by remember { mutableLongStateOf(0L) }
    var hud by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<PlayerDialog?>(null) }
    var brightness by remember {
        mutableFloatStateOf(activity.window.attributes.screenBrightness.let { if (it < 0f) 0.5f else it })
    }
    var volume by remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        )
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                duration = exo.duration.coerceAtLeast(0L)
            }
            override fun onTracksChanged(t: Tracks) { tracks = t }
            override fun onMediaMetadataChanged(m: MediaMetadata) { mediaTitle = m.title?.toString().orEmpty() }
            override fun onPlaybackParametersChanged(p: PlaybackParameters) { speed = p.speed }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }

    LaunchedEffect(exo) {
        while (true) {
            if (!seeking) {
                position = exo.currentPosition
                duration = exo.duration.coerceAtLeast(0L)
            }
            delay(500)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, seeking, dialog) {
        if (controlsVisible && isPlaying && !seeking && dialog == null) {
            delay(3500)
            controlsVisible = false
        }
    }

    LaunchedEffect(hud) {
        if (hud != null) {
            delay(900)
            hud = null
        }
    }

    fun seekBy(deltaMs: Long) {
        val max = if (duration > 0) duration else Long.MAX_VALUE
        exo.seekTo((exo.currentPosition + deltaMs).coerceIn(0L, max))
        hud = if (deltaMs > 0) "+${deltaMs / 1000} dtk" else "${deltaMs / 1000} dtk"
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exo
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            update = { it.resizeMode = resizeModes[resizeIndex].first },
            modifier = Modifier.fillMaxSize(),
        )

        // Lapisan gestur: tap, double-tap, geser kiri/kanan/atas/bawah
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(locked) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            if (!locked) seekBy(if (offset.x < size.width / 2f) -10_000L else 10_000L)
                        },
                    )
                }
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    var onLeft = false
                    detectVerticalDragGestures(
                        onDragStart = { onLeft = it.x < size.width / 2f },
                        onVerticalDrag = { _, dy ->
                            val delta = -dy / size.height
                            if (onLeft) {
                                brightness = (brightness + delta).coerceIn(0.02f, 1f)
                                val lp = activity.window.attributes
                                lp.screenBrightness = brightness
                                activity.window.attributes = lp
                                hud = "Kecerahan ${(brightness * 100).roundToInt()}%"
                            } else {
                                volume = (volume + delta).coerceIn(0f, 1f)
                                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * max).roundToInt(), 0)
                                hud = "Volume ${(volume * 100).roundToInt()}%"
                            }
                        },
                    )
                }
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            seeking = true
                            seekPreview = position
                        },
                        onDragEnd = {
                            exo.seekTo(seekPreview)
                            position = seekPreview
                            seeking = false
                        },
                        onDragCancel = { seeking = false },
                        onHorizontalDrag = { _, dx ->
                            val max = if (duration > 0) duration else Long.MAX_VALUE
                            seekPreview = (seekPreview + (dx * 100).toLong()).coerceIn(0L, max)
                            hud = "${formatTime(seekPreview)} / ${formatTime(duration)}"
                        },
                    )
                }
        )

        if (buffering && !inPip) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(56.dp), color = Color.White)
        }

        hud?.let {
            if (!inPip) {
                Text(
                    it,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 56.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !inPip && !locked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Bar atas
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.7f), Color.Transparent)))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", tint = Color.White)
                    }
                    Text(
                        mediaTitle,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { dialog = PlayerDialog.Audio }) {
                        Icon(Icons.Rounded.Audiotrack, "Audio", tint = Color.White)
                    }
                    IconButton(onClick = { dialog = PlayerDialog.Subtitle }) {
                        Icon(Icons.Rounded.Subtitles, "Subtitle", tint = Color.White)
                    }
                    IconButton(onClick = onEnterPip) {
                        Icon(Icons.Rounded.PictureInPictureAlt, "PiP", tint = Color.White)
                    }
                }

                // Kontrol tengah
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    IconButton(onClick = { exo.seekToPreviousMediaItem() }, enabled = exo.hasPreviousMediaItem()) {
                        Icon(Icons.Rounded.SkipPrevious, "Sebelumnya", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    IconButton(onClick = { seekBy(-10_000L) }) {
                        Icon(Icons.Rounded.Replay10, "Mundur 10 detik", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    Box(
                        Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                if (exo.isPlaying) exo.pause() else {
                                    if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
                                    exo.play()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (isPlaying) "Jeda" else "Putar",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    IconButton(onClick = { seekBy(10_000L) }) {
                        Icon(Icons.Rounded.Forward10, "Maju 10 detik", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    IconButton(onClick = { exo.seekToNextMediaItem() }, enabled = exo.hasNextMediaItem()) {
                        Icon(Icons.Rounded.SkipNext, "Berikutnya", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                }

                // Bar bawah
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.75f))))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Slider(
                        value = (if (seeking) seekPreview else position).toFloat(),
                        onValueChange = {
                            seeking = true
                            seekPreview = it.toLong()
                        },
                        onValueChangeFinished = {
                            exo.seekTo(seekPreview)
                            position = seekPreview
                            seeking = false
                        },
                        valueRange = 0f..maxOf(duration, 1L).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${formatTime(if (seeking) seekPreview else position)} / ${formatTime(duration)}",
                            color = Color.White,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { dialog = PlayerDialog.Speed }) {
                            Text(speed.toString().removeSuffix(".0") + "x", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { resizeIndex = (resizeIndex + 1) % resizeModes.size; hud = resizeModes[resizeIndex].second }) {
                            Icon(Icons.Rounded.AspectRatio, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(resizeModes[resizeIndex].second, color = Color.White, fontSize = 13.sp)
                        }
                        IconButton(onClick = onRotate) {
                            Icon(Icons.Rounded.ScreenRotation, "Putar layar", tint = Color.White)
                        }
                        IconButton(onClick = { locked = true; controlsVisible = false; hud = "Layar terkunci" }) {
                            Icon(Icons.Rounded.Lock, "Kunci", tint = Color.White)
                        }
                    }
                }
            }
        }

        // Tombol buka kunci (hanya muncul saat terkunci & disentuh)
        AnimatedVisibility(
            visible = locked && controlsVisible && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp),
        ) {
            IconButton(
                onClick = { locked = false; hud = "Layar dibuka" },
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)),
            ) {
                Icon(Icons.Rounded.LockOpen, "Buka kunci", tint = Color.White)
            }
        }
    }

    // ---- Dialog ----
    when (dialog) {
        PlayerDialog.Speed -> ChoiceDialog(
            title = "Kecepatan",
            labels = speeds.map { it.toString().removeSuffix(".0") + "x" },
            selected = speeds.indexOfFirst { it == speed },
            onSelect = { exo.setPlaybackSpeed(speeds[it]); dialog = null },
            onDismiss = { dialog = null },
        )

        PlayerDialog.Audio -> {
            val items = tracks.groups
                .filter { it.type == C.TRACK_TYPE_AUDIO }
                .flatMap { g -> (0 until g.length).map { g to it } }
            if (items.isEmpty()) {
                ChoiceDialog("Trek audio", listOf("Tidak ada trek audio"), -1, { dialog = null }, { dialog = null })
            } else {
                ChoiceDialog(
                    title = "Trek audio",
                    labels = items.mapIndexed { n, (g, i) -> trackLabel(g.getTrackFormat(i), n) },
                    selected = items.indexOfFirst { (g, i) -> g.isTrackSelected(i) },
                    onSelect = { selectTrack(exo, items[it].first, items[it].second); dialog = null },
                    onDismiss = { dialog = null },
                )
            }
        }

        PlayerDialog.Subtitle -> {
            val items = tracks.groups
                .filter { it.type == C.TRACK_TYPE_TEXT }
                .flatMap { g -> (0 until g.length).map { g to it } }
            val textOff = exo.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
            val selectedIdx = items.indexOfFirst { (g, i) -> g.isTrackSelected(i) }
            ChoiceDialog(
                title = "Subtitle",
                labels = listOf("Mati") + items.mapIndexed { n, (g, i) -> trackLabel(g.getTrackFormat(i), n) },
                selected = if (textOff || selectedIdx < 0) 0 else selectedIdx + 1,
                onSelect = {
                    if (it == 0) {
                        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                    } else {
                        selectTrack(exo, items[it - 1].first, items[it - 1].second)
                    }
                    dialog = null
                },
                onDismiss = { dialog = null },
                footer = {
                    TextButton(onClick = { dialog = null; onPickSubtitle() }) { Text("Muat dari file…") }
                },
            )
        }

        null -> Unit
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                labels.forEachIndexed { i, label ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(i) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = i == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label)
                    }
                }
                footer?.invoke()
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

private fun selectTrack(player: Player, group: Tracks.Group, index: Int) {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(group.type, false)
        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
        .build()
}

private fun trackLabel(format: Format, number: Int): String {
    val language = format.language
        ?.takeIf { it != "und" }
        ?.let { Locale(it).displayLanguage }
        ?.takeIf { it.isNotBlank() }
    val base = format.label ?: language ?: "Trek ${number + 1}"
    val isAudio = format.sampleMimeType?.startsWith("audio") == true
    val channels = if (isAudio && format.channelCount > 0) " · ${format.channelCount}ch" else ""
    return base + channels
}
