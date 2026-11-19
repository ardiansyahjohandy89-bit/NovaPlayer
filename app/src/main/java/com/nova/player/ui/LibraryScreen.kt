package com.nova.player.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.nova.player.PlayerActivity
import com.nova.player.data.VideoItem
import com.nova.player.data.VideoRepository
import com.nova.player.util.formatSize
import com.nova.player.util.formatTime

private enum class SortMode(val label: String) {
    NEWEST("Terbaru"), NAME("Nama"), DURATION("Durasi"), SIZE("Ukuran")
}

private fun playList(context: Context, list: List<VideoItem>, index: Int) {
    val intent = Intent(context, PlayerActivity::class.java)
        .putStringArrayListExtra(PlayerActivity.EXTRA_URIS, ArrayList(list.map { it.uri.toString() }))
        .putStringArrayListExtra(PlayerActivity.EXTRA_TITLES, ArrayList(list.map { it.title }))
        .putExtra(PlayerActivity.EXTRA_INDEX, index)
    context.startActivity(intent)
}

private fun playSingle(context: Context, uri: Uri) {
    val intent = Intent(context, PlayerActivity::class.java)
        .setData(uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(intent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }

    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(granted) {
        if (granted) {
            loading = true
            videos = VideoRepository.load(context)
            loading = false
        }
    }

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var folder by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(SortMode.NEWEST) }
    var sortMenu by remember { mutableStateOf(false) }
    var urlDialog by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            playSingle(context, uri)
        }
    }

    val loader = remember {
        ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build()
    }

    val folders = remember(videos) { videos.map { it.folder }.distinct().sorted() }
    val shown = remember(videos, query, folder, sort) {
        videos
            .filter { (folder == null || it.folder == folder) && it.title.contains(query, ignoreCase = true) }
            .let { list ->
                when (sort) {
                    SortMode.NEWEST -> list.sortedByDescending { it.dateAdded }
                    SortMode.NAME -> list.sortedBy { it.title.lowercase() }
                    SortMode.DURATION -> list.sortedByDescending { it.durationMs }
                    SortMode.SIZE -> list.sortedByDescending { it.size }
                }
            }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    if (searching) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Cari video…") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                    } else {
                        Text("NovaPlayer", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) query = ""
                    }) {
                        Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "Cari")
                    }
                    IconButton(onClick = { urlDialog = true }) { Icon(Icons.Rounded.Link, "Stream URL") }
                    IconButton(onClick = { filePicker.launch(arrayOf("video/*")) }) {
                        Icon(Icons.Rounded.FolderOpen, "Buka file")
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.Rounded.Sort, "Urutkan") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortMode.values().forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    onClick = { sort = mode; sortMenu = false },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !granted -> CenterMessage(
                    title = "Izinkan akses video",
                    body = "NovaPlayer butuh izin untuk menampilkan video di perangkatmu.",
                    action = "Beri izin",
                    onAction = { permissionLauncher.launch(permission) },
                )

                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                videos.isEmpty() -> CenterMessage(
                    title = "Belum ada video",
                    body = "Tidak ada video ditemukan. Kamu tetap bisa membuka file atau stream dari URL lewat ikon di atas.",
                )

                else -> Column(Modifier.fillMaxSize()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(selected = folder == null, onClick = { folder = null }, label = { Text("Semua") })
                        }
                        folders.size.let { _ ->
                            folders.forEach { name ->
                                item(key = name) {
                                    FilterChip(
                                        selected = folder == name,
                                        onClick = { folder = if (folder == name) null else name },
                                        label = { Text(name) },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(shown, key = { _, v -> v.id }) { index, video ->
                            VideoRow(video, loader) { playList(context, shown, index) }
                        }
                    }
                }
            }
        }
    }

    if (urlDialog) {
        AlertDialog(
            onDismissRequest = { urlDialog = false },
            title = { Text("Putar dari URL") },
            text = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("https://…/video.mp4 atau .m3u8") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val link = url.trim()
                    if (link.startsWith("http://") || link.startsWith("https://")) {
                        urlDialog = false
                        playSingle(context, Uri.parse(link))
                    }
                }) { Text("Putar") }
            },
            dismissButton = { TextButton(onClick = { urlDialog = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun VideoRow(video: VideoItem, loader: ImageLoader, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(128.dp)
                .height(72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(video.uri).crossfade(true).build(),
                imageLoader = loader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                formatTime(video.durationMs),
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${video.folder} · ${formatSize(video.size)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun CenterMessage(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.VideoLibrary, null,
            Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}
