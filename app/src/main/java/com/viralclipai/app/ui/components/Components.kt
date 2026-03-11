package com.viralclipai.app.ui.components

import androidx.compose.foundation.background
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
fun ServerStatusBar(connected: Boolean, statusText: String, serverVersion: String = "") {
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
        Text(
            statusText,
            fontSize = 13.sp,
            color = if (connected) Color(0xFF4CAF50) else Color(0xFFFF5252)
        )
        Spacer(Modifier.weight(1f))
        Text(if (serverVersion.isNotEmpty()) "v$serverVersion" else "v5.7.0", fontSize = 11.sp, color = Color.Gray)
    }
}
