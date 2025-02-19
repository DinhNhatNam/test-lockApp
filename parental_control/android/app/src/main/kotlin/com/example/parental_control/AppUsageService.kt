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

class AppUsageService(private val context: Context) {
    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val componentName = ComponentName(context, AdminReceiver::class.java)

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
        try {
            // Kiểm tra quyền Device Admin
            if (!devicePolicyManager.isAdminActive(componentName)) {
                Log.e("AppUsageService", "Device admin permission not granted")
                return false
            }

            // Kiểm tra package name hợp lệ
            if (packageName.isEmpty()) {
                Log.e("AppUsageService", "Invalid package name")
                return false
            }

            // Thử chặn ứng dụng
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    devicePolicyManager.setPackagesSuspended(
                        componentName,
                        arrayOf(packageName),
                        isBlocked
                    )
                } else {
                    devicePolicyManager.setApplicationHidden(
                        componentName,
                        packageName,
                        isBlocked
                    )
                }
                Log.d("AppUsageService", "Successfully ${if (isBlocked) "blocked" else "unblocked"} app: $packageName")
                return true
            } catch (e: SecurityException) {
                Log.e("AppUsageService", "Security exception while blocking app: ${e.message}")
                return false
            }
        } catch (e: Exception) {
            Log.e("AppUsageService", "Error blocking app: ${e.message}")
            return false
        }
    }
 
 
 fun isAppBlocked(packageName: String): Boolean {
    try {
        // Kiểm tra quyền Device Admin
        if (!devicePolicyManager.isAdminActive(componentName)) {
            Log.e("AppUsageService", "Device admin permission not granted")
            return false
        }

        // Kiểm tra package name hợp lệ
        if (packageName.isEmpty()) {
            Log.e("AppUsageService", "Invalid package name")
            return false
        }

        val isBlocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            devicePolicyManager.isPackageSuspended(componentName, packageName)
        } else {
            devicePolicyManager.isApplicationHidden(componentName, packageName)
        }
        
        return isBlocked
        
    } catch (e: SecurityException) {
        Log.e("AppUsageService", "Security exception while checking app status: ${e.message}")
        return false
    } catch (e: Exception) {
        Log.e("AppUsageService", "Error checking if app is blocked: ${e.message}")
        return false
    }
}

    fun getAppUsageStats(): Map<String, Long> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (24 * 60 * 60 * 1000) // 24 hours ago

        return usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            .associate { it.packageName to it.totalTimeInForeground }
    }

    fun setTimeLimit(packageName: String, limitInMinutes: Int) {
        // TODO: Implement time limit logic
    }
}