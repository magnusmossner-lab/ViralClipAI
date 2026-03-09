package com.viralclipai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viralclipai.app.data.models.ContentFilter
import com.viralclipai.app.data.models.SubtitleConfig
import com.viralclipai.app.data.models.CaptionConfig
import com.viralclipai.app.viewmodel.MainViewModel

@Composable
fun HomeScreen(vm: MainViewModel, onGalleryClick: () -> Unit = {}) {
    val uiState by vm.uiState.collectAsState()
    val contentFilter by vm.contentFilter.collectAsState()
    val subtitleConfig by vm.subtitleConfig.collectAsState()
    val captionConfig by vm.captionConfig.collectAsState()
    var youtubeUrl by remember { mutableStateOf("") }
    var activeTab by remember { mutableIntStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Spacer(Modifier.height(12.dp))
        Text("\uD83D\uDD25 ViralClip AI", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Text("v5.4.0 \u2022 YouTube + Galerie \u2192 Virale Clips", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))

        // URL Input
        OutlinedTextField(
            value = youtubeUrl,
            onValueChange = { youtubeUrl = it },
            label = { Text("YouTube-Link") },
            placeholder = { Text("https://youtube.com/watch?v=...") },
            leadingIcon = { Icon(Icons.Default.Link, null) },
            trailingIcon = {
                if (youtubeUrl.isNotEmpty()) IconButton(onClick = { youtubeUrl = "" }) { Icon(Icons.Default.Clear, null) }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            enabled = !uiState.isProcessing
        )
        Spacer(Modifier.height(16.dp))

        // Tab Row
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("\uD83C\uDFAF Content", fontSize = 12.sp) })
            Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("\uD83D\uDCDD Untertitel", fontSize = 12.sp) })
            Tab(selected = activeTab == 2, onClick = { activeTab = 2 }, text = { Text("\uD83D\uDCAC Caption", fontSize = 12.sp) })
        }
        Spacer(Modifier.height(12.dp))

        when (activeTab) {
            0 -> ContentFilterTab(vm, contentFilter, !uiState.isProcessing)
            1 -> SubtitleTab(vm, subtitleConfig, !uiState.isProcessing)
            2 -> CaptionTab(vm, captionConfig, !uiState.isProcessing)
        }
        Spacer(Modifier.height(20.dp))

        // Process Button
        Button(
            onClick = { vm.processVideo(youtubeUrl) },
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

        // v5.4.0: Gallery Import Button
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onGalleryClick,
            enabled = !uiState.isProcessing,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.VideoLibrary, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("VIDEO AUS GALERIE", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        // Progress
        if (uiState.isProcessing) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = uiState.progress / 100f,
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Text("${uiState.progress}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }

        // Error / Success messages
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
        Spacer(Modifier.height(24.dp))
    }
}

// Content Filter Tab
@Composable
fun ContentFilterTab(viewModel: MainViewModel, filter: ContentFilter, enabled: Boolean) {
    val languages = listOf("auto" to "\uD83C\uDF0D Auto", "de" to "\uD83C\uDE01 Deutsch", "en" to "\uD83C\uDDEC\uD83C\uDDE7 English", "tr" to "\uD83C\uDDF9\uD83C\uDDF7 Türkçe", "ar" to "\uD83C\uDDF8\uD83C\uDDE6 العربية", "es" to "\uD83C\uDDEA\uD83C\uDDF8 Español", "fr" to "\uD83C\uDDEB\uD83C\uDDF7 Français")
    val moods = listOf("all" to "\uD83C\uDFAF Alle", "kontrovers" to "\uD83D\uDD25 Kontrovers", "emotional" to "\uD83D\uDE22 Emotional", "lustig" to "\uD83D\uDE02 Lustig", "realtalk" to "\uD83D\uDCAF Realtalk", "drama" to "\uD83C\uDFAD Drama", "motivation" to "\uD83D\uDCAA Motivation", "skandal" to "\uD83D\uDEA8 Skandal")
    val sensitivities = listOf("low" to "Wenig (1-3)", "medium" to "Mittel (3-6)", "high" to "Viel (6-12)")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Language
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp)) {
                Text("\uD83C\uDF10 Sprache", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    languages.forEach { (code, label) ->
                        FilterChip(
                            selected = filter.language == code,
                            onClick = { if (enabled) viewModel.updateContentFilter(language = code) },
                            label = { Text(label, fontSize = 12.sp) },
                            enabled = enabled
                        )
                    }
                }
            }
        }

        // Mood
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp)) {
                Text("\uD83D\uDD25 Thema / Stimmung", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    moods.forEach { (key, label) ->
                        FilterChip(
                            selected = filter.mood == key,
                            onClick = { if (enabled) viewModel.updateContentFilter(mood = key) },
                            label = { Text(label, fontSize = 12.sp) },
                            enabled = enabled
                        )
                    }
                }
            }
        }

        // Keywords
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp)) {
                Text("#\uFE0F\u20E3 Schlüsselwörter", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                var newKeyword by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = newKeyword,
                    onValueChange = { newKeyword = it },
                    placeholder = { Text("Keyword eingeben...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        viewModel.addKeyword(newKeyword)
                        newKeyword = ""
                    }),
                    trailingIcon = {
                        if (newKeyword.isNotEmpty()) {
                            IconButton(onClick = { viewModel.addKeyword(newKeyword); newKeyword = "" }) {
                                Icon(Icons.Default.Add, null)
                            }
                        }
                    }
                )
                if (filter.keywords.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        filter.keywords.forEach { kw ->
                            InputChip(
                                selected = true,
                                onClick = { if (enabled) viewModel.removeKeyword(kw) },
                                label = { Text(kw, fontSize = 12.sp) },
                                trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) }
                            )
                        }
                    }
                }
                val suggestions = when (filter.mood) {
                    "kontrovers" -> listOf("Skandal", "Enthüllung", "Wahrheit", "Lüge", "Betrug")
                    "emotional" -> listOf("Tränen", "Gänsehaut", "Herz", "Liebe", "Abschied")
                    "lustig" -> listOf("Fail", "Peinlich", "Lachen", "Witzig", "Cringe")
                    "realtalk" -> listOf("Ehrlich", "Klartext", "Meinung", "Fakten", "Real")
                    "drama" -> listOf("Streit", "Beef", "Eskalation", "Ausraster", "Konfrontation")
                    "motivation" -> listOf("Erfolg", "Hustle", "Mindset", "Grind", "Aufstehen")
                    "skandal" -> listOf("Schock", "Aufdeckung", "Geheim", "Vertuschung", "Beweis")
                    else -> listOf("Viral", "Reaktion", "Highlight", "Moment", "Eskalation")
                }
                Spacer(Modifier.height(8.dp))
                Text("Vorschläge:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.filter { it !in filter.keywords }.forEach { s ->
                        SuggestionChip(
                            onClick = { viewModel.addKeyword(s) },
                            label = { Text(s, fontSize = 11.sp) },
                            enabled = enabled
                        )
                    }
                }
            }
        }

        // Viral Sensitivity
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp)) {
                Text("\u26A1 Viral-Empfindlichkeit", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sensitivities.forEach { (key, label) ->
                        FilterChip(
                            selected = filter.viralSensitivity == key,
                            onClick = { if (enabled) viewModel.updateContentFilter(viralSensitivity = key) },
                            label = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            enabled = enabled
                        )
                    }
                }
            }
        }
    }
}

// Subtitle Tab
@Composable
fun SubtitleTab(viewModel: MainViewModel, config: SubtitleConfig, enabled: Boolean) {
    val fonts = listOf("Anton", "Bebas Neue", "Montserrat", "Oswald", "Poppins", "Bangers")
    val sizes = listOf("small" to "Klein (36px)", "medium" to "Mittel (48px)", "large" to "Groß (64px)", "xl" to "XL (80px)")
    val textColors = listOf("#FFFFFF" to "Weiß", "#FFD700" to "Gelb", "#00FFFF" to "Cyan", "#00FF88" to "Grün", "#FF3333" to "Rot", "#FF6B35" to "Orange", "#FF69B4" to "Pink", "#B388FF" to "Lila")
    val highlightColors = listOf("#00FF88" to "Neon-Grün", "#FFD700" to "Gelb", "#00FFFF" to "Cyan", "#FF3333" to "Rot", "#FF6B35" to "Orange", "#FF1493" to "Hot Pink")
    val styles = listOf("karaoke" to "\uD83C\uDFA4 Karaoke", "classic" to "\uD83D\uDCDD Klassisch", "neon" to "\u2728 Neon Glow", "box" to "\uD83D\uDD32 Box", "outline" to "\uD83D\uDD24 Outline")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Style
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp)) {
                Text("\uD83C\uDFA8 Stil", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    styles.forEach { (key, label) ->
                        FilterChip(
                            selected = config.style == key,
                            onClick = { if (enabled) viewModel.updateSubtitleConfig(style = key) },
                            label = { Text(label, fontSize = 12.sp) },
                            enabled = enabled
                        )
                    }
                }
            }
        }

        // Font
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp)) {
                Text("\uD83D\uDD24 Schriftart", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    fonts.forEach { font ->
                        FilterChip(
                            selected = config.fontFamily == font,
                            onClick = { if (enabled) viewModel.updateSubtitleConfig(fontFamily = font) },
                            label = { Text(font, fontSize = 12.sp) },
                            enabled = enabled
                        )
                    }
                }
            }
        }

        // Size
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp)) {
                Text("\uD83D\uDD0D Größe", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sizes.forEach { (key, label) ->
                        FilterChip(
                            selected = config.fontSize == key,
                            onClick = { if (enabled) viewModel.updateSubtitleConfig(fontSize = key) },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            enabled = enabled
                        )
                    }
                }
            }
        }

        // Text Color
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp)) {
                Text("\uD83C\uDFA8 Textfarbe", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    textColors.forEach { (hex, name) ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(if (config.textColor == hex) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                    .clickable(enabled = enabled) { viewModel.updateSubtitleConfig(textColor = hex) }
                            )
                            Text(name, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Highlight Color (for Karaoke)
        if (config.style == "karaoke") {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp)) {
                    Text("\u2728 Highlight-Farbe (aktives Wort)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        highlightColors.forEach { (hex, name) ->
                            val color = Color(android.graphics.Color.parseColor(hex))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .then(if (config.highlightColor == hex) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                        .clickable(enabled = enabled) { viewModel.updateSubtitleConfig(highlightColor = hex) }
                                )
                                Text(name, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Preview
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))) {
            Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Vorschau", fontSize = 11.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                val previewColor = Color(android.graphics.Color.parseColor(config.textColor))
                val hlColor = Color(android.graphics.Color.parseColor(config.highlightColor))
                Row {
                    Text("DAS ", color = previewColor, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text("HAT ", color = hlColor, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text("ER GESAGT", color = previewColor, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                }
            }
        }
    }
}

// Caption Tab
@Composable
fun CaptionTab(viewModel: MainViewModel, config: CaptionConfig, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("\uD83D\uDCAC Hook-Caption", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Switch(checked = config.enabled, onCheckedChange = { viewModel.updateCaptionConfig(enabled = it) }, enabled = enabled)
                }
                if (config.enabled) {
                    Spacer(Modifier.height(8.dp))
                    Text("Weißer Kasten oben im Video (erste 3 Sekunden)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = config.text,
                        onValueChange = { viewModel.updateCaptionConfig(text = it) },
                        label = { Text("Caption-Text (leer = KI generiert)") },
                        placeholder = { Text("z.B. DAS HAT ER WIRKLICH GESAGT \uD83D\uDE31") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        enabled = enabled,
                        maxLines = 2
                    )
                }
            }
        }

        // Preview
        if (config.enabled) {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Vorschau", fontSize = 11.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Text(
                            text = config.text.ifEmpty { "KI generiert Hook-Text \uD83D\uDD25" },
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp)) {
                Text("\uD83D\uDCA1 Tipp", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Leer lassen = KI erstellt automatisch einen Hook basierend auf dem Videoinhalt. Oder eigenen Text eingeben!", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
