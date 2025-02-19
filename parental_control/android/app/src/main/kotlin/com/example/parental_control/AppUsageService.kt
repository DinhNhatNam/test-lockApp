package com.example.parental_control

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.util.Log
import android.app.ActivityManager
import android.content.Intent
import android.provider.Settings
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Build
import android.os.Process
import android.graphics.PixelFormat
import android.view.WindowManager
import android.view.Gravity
import android.widget.TextView
import android.graphics.Color
import android.view.ViewGroup.LayoutParams
import android.os.Handler
import android.os.Looper

class AppUsageService(private val context: Context) {
    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val componentName = ComponentName(context, AdminReceiver::class.java)
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val blockedApps = mutableSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var monitoringRunnable: Runnable? = null
    private var isMonitoring = false

    fun getInstalledApps(): List<Map<String, String>> {
        Log.d("AppUsageService", "=== Native: Starting getInstalledApps ===")
        val packageManager = context.packageManager
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        Log.d("AppUsageService", "=== Native: Found ${apps.size} installed apps ===")
        
        return apps.map { app ->
            mapOf(
                "packageName" to app.packageName,
                "appName" to (packageManager.getApplicationLabel(app)?.toString() ?: "Unknown"),
                "isSystemApp" to (((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0).toString())
            )
        }.also {
            Log.d("AppUsageService", "=== Native: Mapped ${it.size} apps ===")
        }
    }

   fun blockApp(packageName: String, isBlocked: Boolean): Boolean {
    return try {
        if (packageName == "com.example.parental_control") {
            Log.d("AppUsageService", "Cannot block parental control app")
            return false
        }

        if (isBlocked) {
            AppBlockerService.blockedApps.add(packageName)
            // Start service nếu chưa chạy
            context.startService(Intent(context, AppBlockerService::class.java))
            Log.d("AppUsageService", "Added to blocked list: $packageName")
        } else {
            AppBlockerService.blockedApps.remove(packageName)
            if (AppBlockerService.blockedApps.isEmpty()) {
                context.stopService(Intent(context, AppBlockerService::class.java))
            }
            Log.d("AppUsageService", "Removed from blocked list: $packageName")
        }
        true
    } catch (e: Exception) {
        Log.e("AppUsageService", "Error in blockApp", e)
        false
    }
}

   fun isAppBlocked(packageName: String): Boolean {
    return AppBlockerService.blockedApps.contains(packageName)
}

   private fun startMonitoring() {
        if (monitoringRunnable != null) return

        monitoringRunnable = object : Runnable {
            override fun run() {
                checkCurrentApp()
                handler.postDelayed(this, 500) // Kiểm tra mỗi 500ms
            }
        }.also {
            handler.post(it)
        }
        
        Log.d("AppUsageService", "Started monitoring with blocked apps: $blockedApps")
        isMonitoring = true
    }
    private fun stopMonitoring() {
        monitoringRunnable?.let {
            handler.removeCallbacks(it)
            monitoringRunnable = null
        }
        isMonitoring = false
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
                
                // Mở settings nếu chưa có quyền Accessibility
                if (!isAccessibilityServiceEnabled()) {
                    val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    showBlockedMessage("Vui lòng bật dịch vụ Accessibility để chặn ứng dụng")
                    return
                }

                // Kill tất cả các processes của ứng dụng bị chặn
                activityManager.killBackgroundProcesses(currentApp)
                
                // Gửi về home screen ngay lập tức
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(homeIntent)
                
                showBlockedMessage()
            }
        } catch (e: Exception) {
            Log.e("AppBlockerService", "Error checking current app", e)
        }
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED, 0
        )
        
        if (accessibilityEnabled == 1) {
            val service = "${context.packageName}/.BlockerAccessibilityService"
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return settingValue?.contains(service) == true
        }
        return false
    }

    private var blockMessageView: TextView? = null

     private fun showBlockedMessage(message: String = "⚠️ Ứng dụng này đã bị chặn ⚠️") {
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

                TextView(context).apply {
                    text = message
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
        try {
            blockMessageView?.let {
                windowManager.removeView(it)
                blockMessageView = null
            }
        } catch (e: Exception) {
            Log.e("AppUsageService", "Error hiding blocked message", e)
        }
    }

    fun getAppUsageStats(): Map<String, Long> {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (24 * 60 * 60 * 1000) // 24 hours ago

        return usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            .associate { it.packageName to it.totalTimeInForeground }
    }

    fun setTimeLimit(packageName: String, limitInMinutes: Int) {
        // TODO: Implement time limit logic
    }
}