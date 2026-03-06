package com.viralclipai.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viralclipai.app.upload.UploadActivity
import com.viralclipai.app.analytics.AnalyticsDashboardActivity
import com.viralclipai.app.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val connected by viewModel.serverConnected.collectAsState()
    val context = LocalContext.current
    var serverUrl by remember { mutableStateOf("http://192.168.1.100:8000") }
    val scrollState = rememberScrollState()

    Column(
        Modifier.fillMaxSize().verticalScroll(scrollState).padding(20.dp)
    ) {
        Text("Einstellungen", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(24.dp))

        // Upload & Analytics Buttons
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Upload & Analytics", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { context.startActivity(Intent(context, UploadActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Upload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clip hochladen (YT/TikTok)")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { context.startActivity(Intent(context, AnalyticsDashboardActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Analytics, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Analytics Dashboard")
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Server Connection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Server-Verbindung", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server-URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.setServerUrl(serverUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(if (connected) Icons.Default.Cloud else Icons.Default.CloudOff, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (connected) "Verbunden \u2713" else "Verbinden")
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Clip Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Clip-Einstellungen", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))

                // Duration
                Text("Min. Dauer: ${uiState.minDuration}s", fontSize = 14.sp)
                Slider(
                    value = uiState.minDuration.toFloat(),
                    onValueChange = { viewModel.updateSettings(minDuration = it.toInt()) },
                    valueRange = 15f..120f,
                    steps = 20
                )
                Text("Max. Dauer: ${uiState.maxDuration}s", fontSize = 14.sp)
                Slider(
                    value = uiState.maxDuration.toFloat(),
                    onValueChange = { viewModel.updateSettings(maxDuration = it.toInt()) },
                    valueRange = 30f..300f,
                    steps = 26
                )
                Spacer(Modifier.height(8.dp))

                // Toggles
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("B-Roll Effekte", fontSize = 14.sp)
                    Switch(checked = uiState.brollEnabled, onCheckedChange = { viewModel.updateSettings(broll = it) })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-Captions", fontSize = 14.sp)
                    Switch(checked = uiState.autoCaptions, onCheckedChange = { viewModel.updateSettings(captions = it) })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-Untertitel", fontSize = 14.sp)
                    Switch(checked = uiState.autoSubtitles, onCheckedChange = { viewModel.updateSettings(subtitles = it) })
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // App Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("ViralClip AI v2.2", fontWeight = FontWeight.Bold)
                Text("Self-Healing Engine \u2713", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("ML Feedback Loop \u2713", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("YouTube + TikTok Upload \u2713", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
