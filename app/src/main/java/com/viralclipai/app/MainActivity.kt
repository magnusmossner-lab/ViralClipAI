package com.viralclipai.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.viralclipai.app.ui.components.ServerStatusBar
import com.viralclipai.app.ui.screens.*
import com.viralclipai.app.ui.theme.ViralClipTheme
import com.viralclipai.app.viewmodel.MainViewModel
class MainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { ViralClipTheme { MainApp() } } } }
@Composable fun MainApp() {
    val vm: MainViewModel = viewModel()
    val connected by vm.serverConnected.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(topBar = { ServerStatusBar(connected, if (connected) "Server verbunden" else "Server offline") }, bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.surface) { NavigationBarItem(icon = { Icon(Icons.Default.Home, "Home") }, label = { Text("Home") }, selected = tab == 0, onClick = { tab = 0 }); NavigationBarItem(icon = { Icon(Icons.Default.VideoLibrary, "Clips") }, label = { Text("Clips") }, selected = tab == 1, onClick = { tab = 1 }); NavigationBarItem(icon = { Icon(Icons.Default.Settings, "Settings") }, label = { Text("Settings") }, selected = tab == 2, onClick = { tab = 2 }) } }) { p -> Box(Modifier.padding(p)) { when (tab) { 0 -> HomeScreen(vm); 1 -> ClipsScreen(vm); 2 -> SettingsScreen(vm) } } }
}
