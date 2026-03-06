package com.viralclipai.app.service
import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
class ClipProcessingService : Service() {
    override fun onCreate() { super.onCreate(); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { val ch = NotificationChannel("viralclip", "Clip Processing", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager::class.java).createNotificationChannel(ch) } }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { startForeground(1, NotificationCompat.Builder(this, "viralclip").setContentTitle("ViralClip AI").setContentText("Clips werden erstellt...").setSmallIcon(android.R.drawable.ic_menu_edit).build()); return START_NOT_STICKY }
    override fun onBind(intent: Intent?): IBinder? = null
}
