package com.example.parental_control

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import android.view.Gravity
import android.graphics.PixelFormat
import android.graphics.Color
import android.view.ViewGroup.LayoutParams
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat

class AppBlockerService : Service() {
    private lateinit var activityManager: ActivityManager
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var blockMessageView: TextView? = null
    private var monitoringRunnable: Runnable? = null
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "AppBlockerService"
        var blockedApps = mutableSetOf<String>()
        private var isMonitoring = false
    }

    override fun onCreate() {
        super.onCreate()
        activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground()
        startMonitoring()
    }
private fun findPidByPackage(packageName: String): Int? {
        try {
            activityManager.runningAppProcesses?.forEach { processInfo ->
                if (processInfo.processName == packageName) {
                    return processInfo.pid
                }
            }
        } catch (e: Exception) {
            Log.e("AppBlockerService", "Error finding PID", e)
        }
        return null
    }

   private fun checkCurrentApp() {
        if (!isMonitoring) return
        
        try {
            val time = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                time - 1000,
                time
            )
            
            val currentApp = stats
                ?.filter { it.lastTimeUsed > time - 1000 }
                ?.maxByOrNull { it.lastTimeUsed }
                ?.packageName

            if (currentApp != null && 
                blockedApps.contains(currentApp) && 
                currentApp != "com.example.parental_control") {
                    
                Log.d("AppBlockerService", "Found blocked app: $currentApp")
                
                // Kill tất cả các processes của ứng dụng bị chặn
                val pid = findPidByPackage(currentApp)
                if (pid != null) {
                    android.os.Process.killProcess(pid)
                }
                activityManager.killBackgroundProcesses(currentApp)
                
                // Gửi về home screen ngay lập tức
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                           Intent.FLAG_ACTIVITY_CLEAR_TOP or
                           Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
                startActivity(homeIntent)
                
                // Hiển thị thông báo chặn
                showBlockedMessage()
                
                // Thêm một delay nhỏ trước khi kiểm tra lại
                Thread.sleep(100)
                
                // Kiểm tra lại và kill lần nữa nếu cần
                if (isAppRunning(currentApp)) {
                    activityManager.killBackgroundProcesses(currentApp)
                }
            }
        } catch (e: Exception) {
            Log.e("AppBlockerService", "Error checking current app", e)
        }
    }

    private fun startMonitoring() {
        if (monitoringRunnable != null) return
        
        isMonitoring = true
        monitoringRunnable = object : Runnable {
            override fun run() {
                if (isMonitoring) {
                    checkCurrentApp()
                    handler.postDelayed(this, 200) // Giảm interval xuống 200ms
                }
            }
        }.also {
            handler.post(it)
        }
        Log.d("AppBlockerService", "Started monitoring with blocked apps: $blockedApps")
    }

    private fun stopMonitoring() {
        isMonitoring = false
        monitoringRunnable?.let { handler.removeCallbacks(it) }
        monitoringRunnable = null
        hideBlockedMessage()
    }

    private fun showBlockedMessage() {
        handler.post {
            if (blockMessageView == null) {
                val params = WindowManager.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP
                }

                TextView(this).apply {
                    text = "⚠️ Ứng dụng này đã bị chặn ⚠️"
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.RED)
                    gravity = Gravity.CENTER
                    setPadding(20, 20, 20, 20)
                    textSize = 18f
                    blockMessageView = this
                    try {
                        windowManager.addView(this, params)
                    } catch (e: Exception) {
                        Log.e("AppBlockerService", "Error showing message", e)
                    }
                }

                handler.postDelayed({
                    hideBlockedMessage()
                }, 3000)
            }
        }
    }

    private fun hideBlockedMessage() {
        blockMessageView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e("AppBlockerService", "Error hiding message", e)
            }
            blockMessageView = null
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "App Blocker Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Parental Control")
            .setContentText("Đang giám sát ứng dụng")
            .setSmallIcon(R.drawable.ic_notification)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }
     private fun isAppRunning(packageName: String): Boolean {
        try {
            activityManager.runningAppProcesses?.forEach { processInfo ->
                if (processInfo.processName == packageName) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e("AppBlockerService", "Error checking if app is running", e)
        }
        return false
    }
}


