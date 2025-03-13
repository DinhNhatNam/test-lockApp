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
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONObject

class BlockerAccessibilityService : AccessibilityService() {
    private lateinit var activityManager: ActivityManager
    private lateinit var windowManager: WindowManager
    private var overlayView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    
    private var currentPackage: String? = null
    private var currentActivity: String? = null
    private var activityStartTime: Long = 0
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var lastLoggedDetails: String? = null
    private var lastLoggedTime: Long = 0
    private val MIN_LOG_INTERVAL = 1000 // Khoảng thời gian tối thiểu giữa 2 log (1 giây)

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
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100L
        }
        serviceInfo = info
        Log.d("BlockerService", "Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            val packageName = event.packageName?.toString()
            
            // Xử lý đặc biệt cho YouTube và TikTok
            when (packageName) {
                "com.google.android.youtube" -> handleYouTubeActivity(event)
                "com.ss.android.ugc.trill",  // TikTok global
                "com.zhiliaoapp.musically",  // TikTok
                "com.ss.android.ugc.trill.go" -> handleTikTokActivity(event) // TikTok Lite
                else -> handleActivityTracking(event)
            }
        }
    }

    private fun handleYouTubeActivity(event: AccessibilityEvent) {
        try {
            val rootNode = event.source ?: return
            
            // Tìm tiêu đề video
            var videoTitle: String? = null
            
            // Tìm trong các view có content-desc hoặc text phù hợp
            rootNode.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/title")
                .firstOrNull()?.let { titleNode ->
                    videoTitle = titleNode.text?.toString()
                }
                
            if (videoTitle != null && videoTitle != currentActivity) {
                currentActivity = videoTitle
                logActivity(
                    type = "ACTIVITY",
                    packageName = "com.google.android.youtube",
                    details = "Đang xem video YouTube: $videoTitle"
                )
            }
            
            rootNode.recycle()
        } catch (e: Exception) {
            Log.e("ActivityTracking", "Error tracking YouTube: ${e.message}")
        }
    }

    private fun handleTikTokActivity(event: AccessibilityEvent) {
        try {
            val rootNode = event.source ?: return
            
            // Tìm thông tin video TikTok
            var description: String? = null
            var creator: String? = null
            var foundValidInfo = false
            
            // Tìm description
            rootNode.findAccessibilityNodeInfosByViewId("com.ss.android.ugc.trill:id/desc")
                .firstOrNull()?.let { descNode ->
                    description = descNode.text?.toString()
                    if (!description.isNullOrBlank()) {
                        foundValidInfo = true
                    }
                }
                
            // Tìm tên creator
            rootNode.findAccessibilityNodeInfosByViewId("com.ss.android.ugc.trill:id/title")
                .firstOrNull()?.let { titleNode ->
                    creator = titleNode.text?.toString()
                    if (!creator.isNullOrBlank()) {
                        foundValidInfo = true
                    }
                }

            // Chỉ log khi tìm thấy thông tin hợp lệ
            if (foundValidInfo) {
                val activityInfo = when {
                    creator != null && description != null -> 
                        "Đang xem video TikTok của $creator: $description"
                    creator != null -> 
                        "Đang xem video TikTok của $creator"
                    description != null -> 
                        "Đang xem video TikTok: $description"
                    else -> null // Không log nếu không có thông tin chi tiết
                }
                
                // Kiểm tra xem thông tin có khác với log trước đó không
                if (activityInfo != null && 
                    activityInfo != lastLoggedDetails &&
                    activityInfo != currentActivity) {
                    
                    currentActivity = activityInfo
                    
                    // Thêm kiểm tra thời gian
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastLoggedTime >= MIN_LOG_INTERVAL) {
                        logActivity(
                            type = "ACTIVITY",
                            packageName = event.packageName.toString(),
                            details = activityInfo
                        )
                    }
                }
            }
            
            rootNode.recycle()
        } catch (e: Exception) {
            Log.e("ActivityTracking", "Error tracking TikTok: ${e.message}")
        }
    }

    private fun handleActivityTracking(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()
        val eventTime = System.currentTimeMillis()
        
        // Danh sách package names cần bỏ qua
        val ignoredPackages = listOf(
            "com.sec.android.app.launcher",  // Samsung launcher
            "com.android.systemui",          // System UI
            "com.android.launcher3",         // Default Android launcher
            "com.samsung.android.app.spage"  // Samsung Free
        )
        
        // Kiểm tra nếu package name nằm trong danh sách bỏ qua
        if (packageName != null && !ignoredPackages.contains(packageName)) {
            if (packageName != currentPackage) {
                // Log app exit
                if (currentPackage != null && !ignoredPackages.contains(currentPackage)) {
                    val appName = getAppName(currentPackage!!)
                    val usedTime = (eventTime - activityStartTime) / 1000 // seconds
                    val usedTimeFormatted = when {
                        usedTime < 60 -> "$usedTime giây"
                        else -> "${usedTime / 60} phút ${usedTime % 60} giây"
                    }
                    
                    // Reset lastLoggedDetails khi chuyển ứng dụng
                    lastLoggedDetails = null
                    
                    logActivity(
                        type = "APP_EXIT",
                        packageName = currentPackage!!,
                        details = "Đã thoát $appName (Sử dụng $usedTimeFormatted)"
                    )
                }
                
                // Log app launch
                currentPackage = packageName
                activityStartTime = eventTime
                val newAppName = getAppName(packageName)
                
                // Reset lastLoggedDetails khi mở ứng dụng mới
                lastLoggedDetails = null
                
                logActivity(
                    type = "APP_LAUNCH",
                    packageName = packageName,
                    details = "Đã mở $newAppName lúc ${dateFormat.format(Date(eventTime))}"
                )
            }

            // Log window title changes
            val windowTitle = event.text.joinToString(" ")
            if (windowTitle.isNotEmpty() && 
                windowTitle != currentActivity && 
                !windowTitle.contains("Trang chủ One UI") &&
                !windowTitle.contains("Samsung Free")) {
                
                currentActivity = windowTitle
                val appName = getAppName(packageName)
                
                val detailsMessage = when (packageName) {
                    "com.google.android.youtube" -> {
                        if (windowTitle.contains("YouTube")) {
                            "Đang trong ứng dụng YouTube"
                        } else {
                            "Đang dùng YouTube: $windowTitle"
                        }
                    }
                    "com.ss.android.ugc.trill",
                    "com.zhiliaoapp.musically",
                    "com.ss.android.ugc.trill.go" -> {
                        if (windowTitle.contains("TikTok")) {
                            "Đang trong ứng dụng TikTok"
                        } else {
                            "Đang dùng TikTok: $windowTitle"
                        }
                    }
                    "com.facebook.katana" -> "Đang dùng Facebook: $windowTitle"
                    "com.google.android.gm" -> "Đang dùng Gmail: $windowTitle"
                    "com.android.chrome" -> "Đang duyệt web: $windowTitle"
                    "com.google.android.apps.messaging" -> "Đang nhắn tin: $windowTitle"
                    "com.whatsapp" -> "Đang dùng WhatsApp: $windowTitle"
                    "com.instagram.android" -> "Đang dùng Instagram: $windowTitle"
                    "com.google.android.apps.photos" -> "Đang xem ảnh: $windowTitle"
                    else -> "Đang dùng $appName: $windowTitle"
                }

                logActivity(
                    type = "ACTIVITY",
                    packageName = packageName,
                    details = detailsMessage
                )
            }
        }
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

    private fun logActivity(type: String, packageName: String, details: String) {
        val timestamp = System.currentTimeMillis()
        
        // Kiểm tra nếu nội dung giống với log trước đó
        if (details == lastLoggedDetails) {
            // Nếu chưa đủ thời gian tối thiểu, bỏ qua
            if (timestamp - lastLoggedTime < MIN_LOG_INTERVAL) {
                return
            }
        }
        
        // Cập nhật thông tin log cuối cùng
        lastLoggedDetails = details
        lastLoggedTime = timestamp

        val appName = getAppName(packageName)
        
        val activityData = JSONObject().apply {
            put("type", type)
            put("packageName", packageName)
            put("appName", appName)
            put("details", details)
            put("timestamp", timestamp)
            put("formattedTime", dateFormat.format(Date(timestamp)))
        }

        // Log locally
        Log.d("ActivityTracking", activityData.toString())
        
        // Send broadcast to MainActivity
        sendBroadcast(Intent("com.example.parental_control.ACTIVITY_TRACKED")
            .putExtra("activity_data", activityData.toString()))
    }

    private fun hideBlockingOverlay() {
        try {
            overlayView?.let {
                windowManager.removeView(it)
                overlayView = null
            }
        } catch (e: Exception) {
            Log.e("BlockerService", "Error hiding overlay", e)
        }
    }

    override fun onInterrupt() {
        Log.d("BlockerService", "Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        hideBlockingOverlay()
    }

    // Tăng khoảng thời gian tối thiểu giữa các log cho TikTok
    companion object {
        private const val MIN_LOG_INTERVAL = 2000 // 2 giây
    }
}