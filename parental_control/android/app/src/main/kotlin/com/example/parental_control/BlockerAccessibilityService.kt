package com.example.parental_control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import android.view.ViewGroup.LayoutParams

class BlockerAccessibilityService : AccessibilityService() {
    private lateinit var activityManager: ActivityManager
    private lateinit var windowManager: WindowManager
    private var overlayView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onCreate() {
        super.onCreate()
        activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 50L
        }
        serviceInfo = info
        Log.d("BlockerService", "Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            
            if (packageName != null && 
                AppBlockerService.blockedApps.contains(packageName) &&
                packageName != "com.example.parental_control") {
                
                Log.d("BlockerService", "Blocking: $packageName")
                
                // Kill ứng dụng
                activityManager.killBackgroundProcesses(packageName)
                
                // Hiển thị overlay chặn
                showBlockingOverlay()
                
                // Quay về home
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                           Intent.FLAG_ACTIVITY_CLEAR_TOP or
                           Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
                startActivity(homeIntent)
                
                // Thực hiện global action HOME
                performGlobalAction(GLOBAL_ACTION_HOME)
                
                // Tự động ẩn overlay sau 3 giây
                handler.postDelayed({
                    hideBlockingOverlay()
                }, 3000)
            }
        }
    }

    private fun showBlockingOverlay() {
        if (overlayView == null) {
            val params = WindowManager.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            TextView(this).apply {
                text = "⚠️ Ứng dụng này đã bị chặn ⚠️"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.RED)
                gravity = Gravity.CENTER
                textSize = 24f
                setPadding(50, 50, 50, 50)
                overlayView = this
                
                try {
                    windowManager.addView(this, params)
                } catch (e: Exception) {
                    Log.e("BlockerService", "Error showing overlay", e)
                }
            }
        }
    }

    private fun hideBlockingOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
                overlayView = null
            } catch (e: Exception) {
                Log.e("BlockerService", "Error hiding overlay", e)
            }
        }
    }

    override fun onInterrupt() {
        Log.d("BlockerService", "Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        hideBlockingOverlay()
    }
}