package com.viralclipai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viralclipai.app.data.api.ApiClient
import com.viralclipai.app.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val serverConnected by viewModel.serverConnected.collectAsState()
    // Use the actual configured server URL as default instead of emulator address
    var serverUrl by remember { mutableStateOf(ApiClient.getBaseUrl().trimEnd('/')) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("\u2699\uFE0F Einstellungen", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(24.dp))

        // Server
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("\uD83C\uDF10 Server", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.setServerUrl(serverUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (serverConnected) "Verbunden \u2713" else "Verbinden")
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Clip Duration
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("\u23F1 Clip-Dauer", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("Min: ${uiState.minDuration}s")
                Slider(
                    value = uiState.minDuration.toFloat(),
                    onValueChange = { viewModel.updateSettings(minDuration = it.toInt()) },
                    valueRange = 15f..120f,
                    steps = 20
                )
                Text("Max: ${uiState.maxDuration}s")
                Slider(
                    value = uiState.maxDuration.toFloat(),
                    onValueChange = { viewModel.updateSettings(maxDuration = it.toInt()) },
                    valueRange = 30f..180f,
                    steps = 29
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // KI Features
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("\uD83E\uDD16 KI-Features", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Toggle("Auto-Cut", "Gesichtserkennung + Zoom", uiState.autoCut) { viewModel.updateSettings(autoCut = it) }
                Toggle("Untertitel", "Karaoke-Stil (Wort f\u00FCr Wort)", uiState.autoSubtitles) { viewModel.updateSettings(subtitles = it) }
                Toggle("Hook-Captions", "Caption in ersten 3s", uiState.autoCaptions) { viewModel.updateSettings(captions = it) }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Info
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Text("ViralClip AI v4.2", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "9:16 Hochformat f\u00FCr TikTok/Reels/Shorts",
                    "Karaoke-Untertitel mit Wort-Highlighting",
                    "Content-Filter: Sprache, Keywords, Themen",
                    "Direkt auf Social Media uploaden",
                    "Clips 1h auf Server gespeichert",
                    "KI lernt aus Downloads & Bewertungen",
                    "Trainiert auf Zarbex/Schradin/Elotrix/Papaplatte",
                    "Kein Schimpfwort-Filter!",
                    "Self-Healing Bug-Fix Engine"
                ).forEach { Text("\u2022 $it", fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun Toggle(title: String, sub: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
