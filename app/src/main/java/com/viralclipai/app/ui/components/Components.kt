package com.viralclipai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            // Title + Virality Badge
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

            // Caption
            Spacer(Modifier.height(8.dp))
            Text(
                "\uD83D\uDCDD \"${clip.caption}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            // Duration + Expiry
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("\u23F1 ${clip.duration.toInt()}s", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("\u23F0 1h verfuegbar", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Tags
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

            // Star Rating Row (FIX: onRate was unused before)
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

            // Download Button
            Spacer(Modifier.height(8.dp))
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
