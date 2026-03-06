package com.viralclipai.app.ui.components

import android.net.Uri
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.viralclipai.app.data.api.ApiClient
import com.viralclipai.app.data.models.ClipData

@Composable
fun VideoPreviewPlayer(clip: ClipData) {
    val context = LocalContext.current
    var isError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    // Build full URL from relative path
    val base = ApiClient.getBaseUrl()
    val videoUrl = remember(clip.previewUrl, base) {
        when {
            clip.previewUrl.startsWith("http") -> clip.previewUrl
            clip.previewUrl.isNotBlank() -> {
                val path = clip.previewUrl.removePrefix("/")
                base.trimEnd('/') + "/" + path
            }
            else -> ""
        }
    }

    if (videoUrl.isBlank()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1A2E)),
            contentAlignment = Alignment.Center
        ) {
            Text("Keine Vorschau verfuegbar", color = Color.White.copy(alpha = 0.6f))
        }
        return
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A2E))
    ) {
        if (isError) {
            // Error state
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.VideocamOff,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Vorschau nicht verfuegbar",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
                if (errorMessage.isNotBlank()) {
                    Text(
                        errorMessage,
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 10.sp
                    )
                }
            }
        } else {
            // ExoPlayer
            val exoPlayer = remember(videoUrl) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
                    repeatMode = Player.REPEAT_MODE_ONE
                    volume = 0f  // Muted by default in preview
                    playWhenReady = true
                    prepare()
                }
            }

            DisposableEffect(videoUrl) {
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_READY -> isLoading = false
                            Player.STATE_BUFFERING -> isLoading = true
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("VideoPreview", "Playback error: ${error.message}", error)
                        isError = true
                        errorMessage = when (error.errorCode) {
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Netzwerkfehler"
                            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Video nicht gefunden"
                            else -> "Fehler ${error.errorCode}"
                        }
                    }
                }
                exoPlayer.addListener(listener)

                onDispose {
                    exoPlayer.removeListener(listener)
                    exoPlayer.release()
                }
            }

            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        controllerAutoShow = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Loading overlay
            if (isLoading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
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
    onRate: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var userRating by remember { mutableIntStateOf(0) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            // Video Preview
            VideoPreviewPlayer(clip = clip)
            Spacer(Modifier.height(12.dp))

            // Header: Title + Virality Score
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        clip.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        "${formatTime(clip.startTime)} - ${formatTime(clip.endTime)} (${clip.duration.toInt()}s)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Virality Score Badge
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                clip.viralityScore >= 0.8f -> Color(0xFF4CAF50)
                                clip.viralityScore >= 0.6f -> Color(0xFFFFC107)
                                else -> Color(0xFFFF5722)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${(clip.viralityScore * 100).toInt()}%",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Caption
            Text(
                clip.caption,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Tags
            if (clip.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    clip.tags.joinToString(" ") { "#$it" },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(12.dp))

            // Action Buttons
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Download Button
                Button(
                    onClick = onDownload,
                    enabled = !isDownloading,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Laden...")
                    } else {
                        Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Download")
                    }
                }

                // Expand/Details Button
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null,
                        Modifier.size(18.dp)
                    )
                }
            }

            // Expanded Section - Rating
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(12.dp))

                Text("Wie gut ist dieser Clip?", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (star in 1..5) {
                        IconButton(
                            onClick = {
                                userRating = star
                                onRate(star)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                if (star <= userRating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$star Sterne",
                                tint = if (star <= userRating) Color(0xFFFFC107) else Color.Gray
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Info badges
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (clip.hasSubtitles) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Untertitel", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.ClosedCaption, null, Modifier.size(14.dp)) }
                        )
                    }
                    if (clip.hasBroll) {
                        AssistChip(
                            onClick = {},
                            label = { Text("B-Roll", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Movie, null, Modifier.size(14.dp)) }
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun ServerStatusBar(connected: Boolean, text: String) {
    Surface(
        color = if (connected) Color(0xFF1B5E20).copy(alpha = 0.9f) else Color(0xFFB71C1C).copy(alpha = 0.9f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (connected) Icons.Default.Cloud else Icons.Default.CloudOff,
                null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(text, color = Color.White, fontSize = 12.sp)
        }
    }
}


private fun formatTime(seconds: Double): String {
    val mins = (seconds / 60).toInt()
    val secs = (seconds % 60).toInt()
    return "%d:%02d".format(mins, secs)
}
