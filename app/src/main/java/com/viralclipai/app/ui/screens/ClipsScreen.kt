package com.viralclipai.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.viralclipai.app.data.api.ApiClient
import com.viralclipai.app.data.models.ClipData
import com.viralclipai.app.ui.components.ViralityBadge
import com.viralclipai.app.viewmodel.MainViewModel

@Composable
fun ClipsScreen(viewModel: MainViewModel) {
    val clips by viewModel.clips.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("\uD83D\uDD25 Deine Clips", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            if (clips.isNotEmpty()) Text("${clips.size} Clips", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Mit Ton, Untertiteln & Caption \u2022 Direkt uploaden", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        if (clips.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.VideoLibrary, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("Noch keine Clips", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("YouTube-Link einf\u00FCgen und Start dr\u00FCcken!", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                itemsIndexed(clips) { i, clip ->
                    ClipCardWithPreview(
                        clip = clip,
                        rank = i + 1,
                        isDownloading = uiState.downloadingClipId == clip.id,
                        isUploading = uiState.uploadingClipId == clip.id,
                        uploadPlatform = uiState.uploadPlatform,
                        onDownload = { viewModel.downloadClip(clip) },
                        onUpload = { platform -> viewModel.uploadToSocial(clip, platform) },
                        onShare = { viewModel.shareClip(clip) },
                        onRate = { viewModel.rateClip(clip.id, it) },
                        viewModel = viewModel
                    )
                }
                item { Spacer(Modifier.height(16.dp)); Text("\u23F0 Clips nach 1h automatisch gel\u00F6scht", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
fun ClipCardWithPreview(
    clip: ClipData,
    rank: Int,
    isDownloading: Boolean,
    isUploading: Boolean,
    uploadPlatform: String?,
    onDownload: () -> Unit,
    onUpload: (String) -> Unit,
    onShare: () -> Unit,
    onRate: (Int) -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    var showPlayer by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Rank badge
            val rankLabel = when (rank) { 1 -> "\uD83D\uDC51 #1 Top Viral"; 2 -> "\uD83D\uDD25 #2 Hot"; 3 -> "\uD83D\uDD25 #3 Hot"; else -> "#$rank" }
            Text(rankLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))

            // Title + Virality
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(clip.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                ViralityBadge(clip.viralityScore)
            }
            Spacer(Modifier.height(8.dp))

            // Caption preview
            if (clip.caption.isNotEmpty()) {
                Text("\uD83D\uDCDD \"${clip.caption}\"", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                Spacer(Modifier.height(8.dp))
            }

            // ─── VIDEO PREVIEW WITH AUDIO ───
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f)
            ) {
                if (showPlayer) {
                    // Use dynamic server URL instead of hardcoded emulator address
                    val serverUrl = remember { ApiClient.getBaseUrl().trimEnd('/') }
                    val player = remember {
                        ExoPlayer.Builder(context).build().apply {
                            setMediaItem(MediaItem.fromUri(Uri.parse("$serverUrl${clip.previewUrl}")))
                            prepare()
                            playWhenReady = true
                            volume = 1f  // AUDIO ON
                        }
                    }
                    DisposableEffect(Unit) { onDispose { player.release() } }

                    AndroidView(
                        factory = {
                            PlayerView(it).apply {
                                this.player = player
                                useController = true
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Play button overlay
                    Box(
                        Modifier.fillMaxSize().background(Color(0xFF1A1A2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { showPlayer = true },
                                modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(40.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("\uD83D\uDD0A Mit Ton abspielen", color = Color.White, fontSize = 13.sp)
                            Text("${clip.duration.toInt()}s \u2022 9:16", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Duration + matched keywords
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("\u23F1 ${clip.duration.toInt()}s", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    if (clip.hasSubtitles) Text("\uD83D\uDCDD ", fontSize = 12.sp)
                    if (clip.hasCaption) Text("\uD83D\uDCAC ", fontSize = 12.sp)
                    Text("\u23F0 1h verf\u00FCgbar", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Tags
            if (clip.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    clip.tags.take(4).forEach { tag ->
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                            Text("#$tag", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Matched Keywords
            if (clip.matchedKeywords.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("\uD83C\uDFAF", fontSize = 11.sp)
                    clip.matchedKeywords.forEach { kw ->
                        Surface(color = Color(0xFF00FF88).copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                            Text(kw, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = Color(0xFF00FF88))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Download button
            Button(
                onClick = onDownload,
                enabled = !isDownloading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Laden...")
                } else {
                    Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clip herunterladen", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))

            // ─── SOCIAL MEDIA UPLOAD BUTTONS ───
            Text("Direkt uploaden:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // TikTok
                OutlinedButton(
                    onClick = { onUpload("tiktok") },
                    enabled = !isUploading,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isUploading && uploadPlatform == "tiktok") CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    else Text("\uD83C\uDFB5 TikTok", fontSize = 12.sp, maxLines = 1)
                }
                // YouTube
                OutlinedButton(
                    onClick = { onUpload("youtube") },
                    enabled = !isUploading,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isUploading && uploadPlatform == "youtube") CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    else Text("\u25B6\uFE0F YouTube", fontSize = 12.sp, maxLines = 1)
                }
                // Instagram
                OutlinedButton(
                    onClick = { onUpload("instagram") },
                    enabled = !isUploading,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isUploading && uploadPlatform == "instagram") CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    else Text("\uD83D\uDCF7 Insta", fontSize = 12.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.height(4.dp))

            // Share button
            TextButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Teilen...", fontSize = 13.sp)
            }
        }
    }
}
