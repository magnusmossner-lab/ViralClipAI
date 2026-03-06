package com.viralclipai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.viralclipai.app.data.api.ApiClient
import com.viralclipai.app.data.models.ClipData

@Composable
fun ViralityBadge(score: Float) {
    val color = when {
        score >= 0.8f -> Color(0xFFFF3366)
        score >= 0.6f -> Color(0xFFFF6B35)
        score >= 0.4f -> Color(0xFFFFD700)
        else -> Color(0xFF888888)
    }
    Surface(color = color.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp)) {
        Text(
            "\uD83D\uDD25 ${(score * 100).toInt()}%",
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPreviewPlayer(
    clip: ClipData,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }

    val videoUrl = remember(clip) {
        val base = ApiClient.getBaseUrl()
        when {
            clip.previewUrl.startsWith("http") -> clip.previewUrl
            clip.previewUrl.isNotBlank() -> base + clip.previewUrl.removePrefix("/")
            clip.downloadUrl.startsWith("http") -> clip.downloadUrl
            clip.downloadUrl.isNotBlank() -> base + clip.downloadUrl.removePrefix("/")
            else -> base + "api/clip/${clip.id}/download"
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
    ) {
        if (isPlaying) {
            val exoPlayer = remember(videoUrl) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(videoUrl))
                    prepare()
                    playWhenReady = true
                    repeatMode = Player.REPEAT_MODE_ONE
                }
            }

            DisposableEffect(Unit) {
                onDispose { exoPlayer.release() }
            }

            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
            )
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clickable { isPlaying = true },
                color = Color(0xFF1A1A2E),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Vorschau abspielen",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "\u25B6 Clip-Vorschau",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "\u23F1 ${clip.duration.toInt()}s \u2022 9:16 Format",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClipCard(
    clip: ClipData,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    clip.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                ViralityBadge(clip.viralityScore)
            }

            // VIDEO PREVIEW
            Spacer(Modifier.height(12.dp))
            VideoPreviewPlayer(clip = clip)

            Spacer(Modifier.height(12.dp))
            Text(
                "\uD83D\uDCDD \"${clip.caption}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("\u23F1 ${clip.duration.toInt()}s", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("\u23F0 1h verfuegbar", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (clip.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    clip.tags.take(3).forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "#$tag", fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bewerten: ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                (1..5).forEach { star ->
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "$star Sterne",
                        tint = Color(0xFFFFD700).copy(alpha = 0.4f),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onRate(star) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onDownload,
                enabled = !isDownloading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Wird in Galerie gespeichert...")
                } else {
                    Icon(Icons.Default.Download, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("\uD83D\uDCF1 In Galerie speichern", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun ServerStatusBar(connected: Boolean, statusText: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (connected) Color(0xFF1B5E20).copy(alpha = 0.3f) else Color(0xFFB71C1C).copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (connected) Icons.Default.Cloud else Icons.Default.CloudOff,
            null,
            tint = if (connected) Color(0xFF4CAF50) else Color(0xFFFF5252),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(statusText, fontSize = 13.sp, color = if (connected) Color(0xFF4CAF50) else Color(0xFFFF5252))
    }
}
