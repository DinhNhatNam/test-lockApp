package com.example.parental_control

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.app.ActivityManager
import android.os.Handler
import android.os.Looper

class AppBlockerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable
    private val checkInterval = 500L // 0.5 giây

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startMonitoring()
    }

    private fun startMonitoring() {
        runnable = Runnable {
            checkCurrentApp()
            handler.postDelayed(runnable, checkInterval)
        }
        handler.post(runnable)
    }

    private fun checkCurrentApp() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val usageEvents = usageStatsManager.queryEvents(time - 1000, time)
        val event = UsageEvents.Event()
        var currentApp: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                currentApp = event.packageName
            }
        }

        currentApp?.let { packageName ->
            val prefs = getSharedPreferences("blocked_apps", Context.MODE_PRIVATE)
            if (prefs.getBoolean(packageName, false)) {
                // Nếu app bị chặn, kill nó
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.killBackgroundProcesses(packageName)
                
                // Quay về home screen
                val homeIntent = Intent(Intent.ACTION_MAIN)
                homeIntent.addCategory(Intent.CATEGORY_HOME)
                homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(homeIntent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }
}