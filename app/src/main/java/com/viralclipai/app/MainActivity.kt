package com.viralclipai.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.viralclipai.app.ui.components.ServerStatusBar
import com.viralclipai.app.ui.screens.*
import com.viralclipai.app.ui.theme.ViralClipTheme
import com.viralclipai.app.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "\u2705 Berechtigungen erteilt! Videos werden in der Galerie gespeichert.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(
                this,
                "\u26A0\uFE0F Ohne Berechtigungen koennen Videos nicht in der Galerie gespeichert werden. Bitte in den Einstellungen erlauben.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStoragePermissions()
        setContent {
            ViralClipTheme {
                MainApp()
            }
        }
    }

    private fun requestStoragePermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= 33) {
            // Android 13+ needs READ_MEDIA_VIDEO
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            // Notification permission for download progress
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else if (Build.VERSION.SDK_INT <= 28) {
            // Android 9 and below needs WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        // Android 10-12: scoped storage via MediaStore, no extra permissions needed for writing

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@Composable
fun MainApp() {
    val vm: MainViewModel = viewModel()
    val connected by vm.serverConnected.collectAsState()
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            ServerStatusBar(connected, if (connected) "Server verbunden" else "Server offline")
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, "Home") },
                    label = { Text("Home") },
                    selected = tab == 0,
                    onClick = { tab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.VideoLibrary, "Clips") },
                    label = { Text("Clips") },
                    selected = tab == 1,
                    onClick = { tab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, "Settings") },
                    label = { Text("Settings") },
                    selected = tab == 2,
                    onClick = { tab = 2 }
                )
            }
        }
    ) { p ->
        Box(Modifier.padding(p)) {
            when (tab) {
                0 -> HomeScreen(vm)
                1 -> ClipsScreen(vm)
                2 -> SettingsScreen(vm)
            }
        }
    }
}
