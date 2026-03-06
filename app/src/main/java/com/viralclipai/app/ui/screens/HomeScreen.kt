package com.viralclipai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viralclipai.app.viewmodel.MainViewModel

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var youtubeUrl by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo
        Text(
            "\uD83C\uDFAC ViralClip AI",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "YouTube \u2192 Virale 9:16 Clips",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))

        // URL Input
        OutlinedTextField(
            value = youtubeUrl,
            onValueChange = { youtubeUrl = it },
            label = { Text("YouTube URL eingeben") },
            placeholder = { Text("https://youtube.com/watch?v=...") },
            leadingIcon = { Icon(Icons.Default.Link, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            enabled = !uiState.isProcessing
        )
        Spacer(Modifier.height(16.dp))

        // Process Button
        Button(
            onClick = { viewModel.processVideo(youtubeUrl) },
            enabled = !uiState.isProcessing && youtubeUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (uiState.isProcessing) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 3.dp)
                Spacer(Modifier.width(12.dp))
                Text(uiState.statusText)
            } else {
                Icon(Icons.Default.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("CLIPS ERSTELLEN", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        // Progress Bar (FIX: Float, not lambda)
        if (uiState.isProcessing) {
            Spacer(Modifier.height(20.dp))
            LinearProgressIndicator(
                progress = uiState.progress / 100f,
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "${uiState.progress}%",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        // Error Message
        uiState.error?.let { e ->
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C).copy(alpha = 0.2f))) {
                Row(Modifier.padding(12.dp)) {
                    Icon(Icons.Default.Error, null, tint = Color(0xFFFF5252))
                    Spacer(Modifier.width(8.dp))
                    Text(e, color = Color(0xFFFF5252), fontSize = 13.sp)
                }
            }
        }

        // Success Message
        uiState.downloadSuccess?.let { m ->
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.2f))) {
                Row(Modifier.padding(12.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                    Spacer(Modifier.width(8.dp))
                    Text(m, color = Color(0xFF4CAF50))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Feature info
        Text(
            "\uD83C\uDFAC 9:16 \u2022 \uD83E\uDD16 Auto-Cut \u2022 \uD83D\uDCDD Untertitel\n\uD83D\uDD25 Ranking \u2022 \uD83C\uDFDE B-Roll \u2022 \u23F0 1h Speicher",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}
