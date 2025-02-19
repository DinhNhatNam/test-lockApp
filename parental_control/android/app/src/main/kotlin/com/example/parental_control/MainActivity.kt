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
                else -> {
                    result.notImplemented()
                }
            }
        }
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
}