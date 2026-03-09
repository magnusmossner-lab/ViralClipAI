package com.viralclipai.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
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
import com.viralclipai.app.update.UpdateManager
import com.viralclipai.app.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private var pendingGalleryCallback: ((Uri?) -> Unit)? = null

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

    // v5.4.0: Gallery video picker
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        pendingGalleryCallback?.invoke(uri)
        pendingGalleryCallback = null
    }

    fun pickVideoFromGallery(callback: (Uri?) -> Unit) {
        pendingGalleryCallback = callback
        galleryLauncher.launch("video/*")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStoragePermissions()

        // v5.4.0: Check for updates on start
        val currentVersionCode = try {
            packageManager.getPackageInfo(packageName, 0).let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode.toInt()
                else @Suppress("DEPRECATION") it.versionCode
            }
        } catch (_: Exception) { 0 }
        UpdateManager.checkOnStart(this, currentVersionCode)

        setContent {
            ViralClipTheme {
                MainApp(activity = this)
            }
        }
    }

    // v5.4.0: Force update check (called from Settings)
    fun forceCheckUpdate() {
        val currentVersionCode = try {
            packageManager.getPackageInfo(packageName, 0).let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode.toInt()
                else @Suppress("DEPRECATION") it.versionCode
            }
        } catch (_: Exception) { 0 }
        UpdateManager.forceCheck(this, currentVersionCode)
    }

    private fun requestStoragePermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= 33) {
            // Android 13+ needs granular media permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
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
        // Android 10-12 don't need any storage permissions for MediaStore

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(activity: MainActivity? = null) {
    val vm: MainViewModel = viewModel()
    val connected by vm.serverConnected.collectAsState()
    val clips by vm.clips.collectAsState()
    var tab by remember { mutableIntStateOf(0) }

    // Auto-switch to Clips tab when clips are ready
    val previousClipCount = remember { mutableIntStateOf(0) }
    LaunchedEffect(clips.size) {
        if (clips.isNotEmpty() && previousClipCount.intValue == 0 && tab == 0) {
            tab = 1  // Switch to Clips tab
        }
        previousClipCount.intValue = clips.size
    }

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
                    icon = {
                        BadgedBox(
                            badge = {
                                if (clips.isNotEmpty()) {
                                    Badge { Text("${clips.size}") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.VideoLibrary, "Clips")
                        }
                    },
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
                0 -> HomeScreen(
                    vm = vm,
                    onGalleryClick = {
                        activity?.pickVideoFromGallery { uri ->
                            uri?.let { vm.processLocalVideo(activity, it) }
                        }
                    }
                )
                1 -> ClipsScreen(vm)
                2 -> SettingsScreen(
                    vm = vm,
                    onCheckUpdate = { activity?.forceCheckUpdate() }
                )
            }
        }
    }
}
