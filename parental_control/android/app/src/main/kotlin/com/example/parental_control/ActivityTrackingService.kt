package com.example.parental_control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class ActivityTrackingService : AccessibilityService() {
    private var currentPackage: String? = null
    private var currentActivity: String? = null
    private var activityStartTime: Long = 0
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100L
        }
        serviceInfo = info
        Log.d("ActivityTracking", "Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()
        val eventTime = System.currentTimeMillis()
        
        if (packageName != currentPackage) {
            // Log app switch
            if (currentPackage != null) {
                logActivity(
                    type = "APP_EXIT",
                    packageName = currentPackage!!,
                    details = "Used for ${(eventTime - activityStartTime) / 1000} seconds"
                )
            }
            
            if (packageName != null) {
                currentPackage = packageName
                activityStartTime = eventTime
                logActivity(
                    type = "APP_LAUNCH",
                    packageName = packageName,
                    details = "Started at ${dateFormat.format(Date(eventTime))}"
                )
            }
        }

        // Log window title changes
        val windowTitle = event.text.joinToString(" ")
        if (windowTitle.isNotEmpty() && windowTitle != currentActivity) {
            currentActivity = windowTitle
            logActivity(
                type = "ACTIVITY",
                packageName = packageName ?: "unknown",
                details = windowTitle
            )
        }
    }

    private fun logActivity(type: String, packageName: String, details: String) {
        val timestamp = System.currentTimeMillis()
        val appName = getAppName(packageName)
        
        val activityData = JSONObject().apply {
            put("type", type)
            put("packageName", packageName)
            put("appName", appName)
            put("details", details)
            put("timestamp", timestamp)
            put("formattedTime", dateFormat.format(Date(timestamp)))
        }

        // Log locally for now, will be replaced with API call later
        Log.d("ActivityTracking", activityData.toString())
        
        // Send broadcast to MainActivity
        sendBroadcast(android.content.Intent("com.example.parental_control.ACTIVITY_TRACKED")
            .putExtra("activity_data", activityData.toString()))
    }

    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = applicationContext.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    override fun onInterrupt() {
        Log.d("ActivityTracking", "Service Interrupted")
    }
}
