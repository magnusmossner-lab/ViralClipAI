#!/usr/bin/env python3
"""Fix all Kotlin compilation errors for ViralClipAI v5.4.0"""
import re, os

REPO = os.path.expanduser("~/ViralClipAI")

# ─── Fix 1: HomeScreen.kt ───
# Problem: parameter named 'viewModel' but MainActivity calls with 'vm = vm'
# Solution: Change parameter back to 'vm' (consistent with SettingsScreen)
hs_path = f"{REPO}/app/src/main/java/com/viralclipai/app/ui/screens/HomeScreen.kt"
with open(hs_path, 'r') as f:
    hs = f.read()

# Change function signature
hs = hs.replace(
    "fun HomeScreen(viewModel: MainViewModel,",
    "fun HomeScreen(vm: MainViewModel,"
)

# Replace viewModel. with vm. ONLY inside HomeScreen function (not sub-functions)
# Find the HomeScreen function body
lines = hs.split('\n')
in_homescreen = False
brace_depth = 0
result_lines = []
for i, line in enumerate(lines):
    if 'fun HomeScreen(' in line:
        in_homescreen = True
        brace_depth = 0
    
    if in_homescreen:
        brace_depth += line.count('{') - line.count('}')
        # Only replace in the HomeScreen function scope
        if 'fun ContentFilterTab(' in line or 'fun SubtitleTab(' in line or 'fun CaptionTab(' in line:
            in_homescreen = False
        else:
            # Replace viewModel. with vm. but not in function definitions of sub-composables
            line = line.replace('viewModel.', 'vm.')
            # Also fix calls: ContentFilterTab(viewModel, -> ContentFilterTab(vm,
            line = line.replace('ContentFilterTab(viewModel,', 'ContentFilterTab(vm,')
            line = line.replace('SubtitleTab(viewModel,', 'SubtitleTab(vm,')
            line = line.replace('CaptionTab(viewModel,', 'CaptionTab(vm,')
    
    result_lines.append(line)

hs = '\n'.join(result_lines)

with open(hs_path, 'w') as f:
    f.write(hs)
print("✅ HomeScreen.kt fixed: viewModel → vm")

# ─── Fix 2: MainViewModel.kt ───
# Problem 1: statusMessage doesn't exist, should be statusText
# Problem 2: pollForResults doesn't exist, should be pollJob
vm_path = f"{REPO}/app/src/main/java/com/viralclipai/app/viewmodel/MainViewModel.kt"
with open(vm_path, 'r') as f:
    vm = f.read()

# Fix statusMessage → statusText
count1 = vm.count('statusMessage')
vm = vm.replace('statusMessage', 'statusText')

# Fix pollForResults → pollJob  
count2 = vm.count('pollForResults')
vm = vm.replace('pollForResults', 'pollJob')

with open(vm_path, 'w') as f:
    f.write(vm)
print(f"✅ MainViewModel.kt fixed: statusMessage→statusText ({count1}x), pollForResults→pollJob ({count2}x)")

# ─── Fix 3: SettingsScreen.kt ───
# Problem 1: Update button appears twice (after title AND after info card)
# Problem 2: Missing Galerie-Import and Auto-Updates in feature list
# Solution: Complete rewrite of the file with clean version
ss_path = f"{REPO}/app/src/main/java/com/viralclipai/app/ui/screens/SettingsScreen.kt"

settings_content = '''package com.viralclipai.app.ui.screens

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
fun SettingsScreen(vm: MainViewModel, onCheckUpdate: () -> Unit = {}) {
    val uiState by vm.uiState.collectAsState()
    val serverConnected by vm.serverConnected.collectAsState()
    // Use the actual configured server URL as default instead of emulator address
    var serverUrl by remember { mutableStateOf(ApiClient.getBaseUrl().trimEnd('/')) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("\\u2699\\uFE0F Einstellungen", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(24.dp))

        // Server
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("\\uD83C\\uDF10 Server", fontWeight = FontWeight.Bold)
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
                    onClick = { vm.setServerUrl(serverUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (serverConnected) "Verbunden \\u2713" else "Verbinden")
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Clip Duration
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("\\u23F1 Clip-Dauer", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("Min: ${uiState.minDuration}s")
                Slider(
                    value = uiState.minDuration.toFloat(),
                    onValueChange = { vm.updateSettings(minDuration = it.toInt()) },
                    valueRange = 15f..120f,
                    steps = 20
                )
                Text("Max: ${uiState.maxDuration}s")
                Slider(
                    value = uiState.maxDuration.toFloat(),
                    onValueChange = { vm.updateSettings(maxDuration = it.toInt()) },
                    valueRange = 30f..180f,
                    steps = 29
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // KI Features
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("\\uD83E\\uDD16 KI-Features", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Toggle("Auto-Cut", "Gesichtserkennung + Zoom", uiState.autoCut) { vm.updateSettings(autoCut = it) }
                Toggle("Untertitel", "Karaoke-Stil (Wort f\\u00FCr Wort)", uiState.autoSubtitles) { vm.updateSettings(subtitles = it) }
                Toggle("Hook-Captions", "Caption in ersten 3s", uiState.autoCaptions) { vm.updateSettings(captions = it) }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Info
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Text("ViralClip AI v5.4.0", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "9:16 Hochformat f\\u00FCr TikTok/Reels/Shorts",
                    "Karaoke-Untertitel mit Wort-Highlighting",
                    "Content-Filter: Sprache, Keywords, Themen",
                    "\\uD83D\\uDCF1 Galerie-Import: Videos direkt hochladen",
                    "\\uD83D\\uDD04 Auto-Updates mit 1-Tap Install",
                    "Direkt auf Social Media uploaden",
                    "Clips 1h auf Server gespeichert",
                    "KI lernt aus Downloads & Bewertungen",
                    "Trainiert auf Zarbex/Schradin/Elotrix/Papaplatte",
                    "Kein Schimpfwort-Filter!",
                    "Self-Healing Bug-Fix Engine"
                ).forEach { Text("\\u2022 $it", fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(16.dp))

        // v5.4.0: Update Check Button
        OutlinedButton(
            onClick = onCheckUpdate,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("\\uD83D\\uDD04 Nach Updates suchen")
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
'''

with open(ss_path, 'w') as f:
    f.write(settings_content)
print("✅ SettingsScreen.kt fixed: clean rewrite with single update button + feature list")

print("\n🎯 All 3 files fixed! Ready to commit and push.")
