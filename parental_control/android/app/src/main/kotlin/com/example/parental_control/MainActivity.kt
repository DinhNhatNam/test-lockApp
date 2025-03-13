package com.example.parental_control

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.app.AppOpsManager
import android.content.Context
import android.provider.Settings
import android.content.Intent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Process
import android.util.Log
import android.os.Bundle
import android.net.Uri
import io.flutter.plugin.common.EventChannel
import android.content.BroadcastReceiver
import android.content.IntentFilter

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.example.parental_control/app_usage"
    private lateinit var appUsageService: AppUsageService
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(this, AdminReceiver::class.java)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        appUsageService = AppUsageService(context)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "checkPermissions" -> {
                    val permissions = checkAllPermissions()
                    Log.d("MainActivity", "Checking permissions: $permissions")
                    result.success(permissions)
                }
                "requestUsagePermission" -> {
                    requestUsageStatsPermission()
                    result.success(true)
                }
                "requestAdminPermission" -> {
                    try {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, 
                            "Cần quyền này để có thể chặn các ứng dụng khác")
                        startActivity(intent)
                        Log.d("MainActivity", "Opening admin settings...")
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error opening admin settings: ${e.message}")
                        result.error("ERROR", e.message, null)
                    }
                }
                "getInstalledApps" -> {
                    val apps = appUsageService.getInstalledApps()
                    result.success(apps)
                }
                "blockApp" -> {
                    val packageName = call.argument<String>("packageName")
                    val isBlocked = call.argument<Boolean>("isBlocked")
                    if (packageName != null && isBlocked != null) {
                        val success = appUsageService.blockApp(packageName, isBlocked)
                        result.success(success)
                    } else {
                        result.error("INVALID_ARGUMENTS", "Package name or isBlocked is null", null)
                    }
                }
                "isAppBlocked" -> {
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        val isBlocked = appUsageService.isAppBlocked(packageName)
                        result.success(isBlocked)
                    } else {
                        result.error("INVALID_ARGUMENTS", "Package name is null", null)
                    }
                }
                "openAccessibilitySettings" -> {
                    openAccessibilitySettings()
                    result.success(true)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }

        // Thêm channel mới cho activity tracking
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.example.parental_control/activity_tracking")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "isTrackingServiceEnabled" -> {
                        val enabled = isAccessibilityServiceEnabled()
                        result.success(enabled)
                    }
                    "openTrackingSettings" -> {
                        try {
                            openAccessibilitySettings()
                            result.success(true)
                        } catch (e: Exception) {
                            result.error("ERROR", e.message, null)
                        }
                    }
                    else -> {
                        result.notImplemented()
                    }
                }
            }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, "com.example.parental_control/activity_events")
            .setStreamHandler(
                object : EventChannel.StreamHandler {
                    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                        // Đăng ký BroadcastReceiver để nhận thông tin hoạt động
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(context: Context?, intent: Intent?) {
                                val activityData = intent?.getStringExtra("activity_data")
                                if (activityData != null) {
                                    events.success(activityData)
                                }
                            }
                        }
                        registerReceiver(
                            receiver,
                            IntentFilter("com.example.parental_control.ACTIVITY_TRACKED")
                        )
                    }

                    override fun onCancel(arguments: Any?) {
                        // Hủy đăng ký receiver khi không cần nữa
                    }
                }
            )
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsageStatsPermission() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.e("MainActivity", "Error requesting usage stats permission: ${e.message}")
        }
    }

    private fun checkAllPermissions(): Map<String, Boolean> {
        val hasUsageStats = hasUsageStatsPermission()
        val isAdmin = devicePolicyManager.isAdminActive(componentName)
        Log.d("MainActivity", "Usage Stats: $hasUsageStats, Admin: $isAdmin")
        return mapOf(
            "usageStats" to hasUsageStats,
            "admin" to isAdmin
        )
    }

    private fun requestAdminPermission() {
    val componentName = ComponentName(this, AdminReceiver::class.java)
    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, 
            "Cần quyền này để có thể chặn các ứng dụng khác")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val accessibilityEnabled = Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
            
            if (accessibilityEnabled == 1) {
                // Lấy danh sách service đã bật
                val settingValue = Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""

                // Tạo các biến tên service có thể có
                val serviceNames = listOf(
                    "$packageName/.BlockerAccessibilityService",
                    "$packageName/com.example.parental_control.BlockerAccessibilityService",
                    "com.example.parental_control/.BlockerAccessibilityService"
                )

                // Log tất cả thông tin để debug
                Log.d("MainActivity", "Accessibility enabled: true")
                Log.d("MainActivity", "Package name: $packageName")
                Log.d("MainActivity", "Enabled services: $settingValue")
                Log.d("MainActivity", "Checking service names: $serviceNames")

                // Kiểm tra xem có service nào trong danh sách được bật không
                val enabledServices = settingValue.split(':')
                val isEnabled = serviceNames.any { serviceName ->
                    enabledServices.any { 
                        it.trim().equals(serviceName, ignoreCase = true)
                    }
                }
                
                Log.d("MainActivity", "Final check result: $isEnabled")
                return isEnabled
            }
            
            Log.d("MainActivity", "Accessibility is not enabled")
            return false
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking accessibility service", e)
            return false
        }
    }
}