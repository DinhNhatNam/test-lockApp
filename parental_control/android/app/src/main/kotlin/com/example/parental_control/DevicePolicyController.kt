package com.example.parental_control

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.util.Log

class DevicePolicyController(private val context: Context) {
    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val componentName = ComponentName(context, DeviceAdminReceiver::class.java)

    fun isProfileOwner(): Boolean {
        return devicePolicyManager.isProfileOwnerApp(context.packageName)
    }

    fun isDeviceOwner(): Boolean {
        return devicePolicyManager.isDeviceOwnerApp(context.packageName)
    }

    fun setApplicationRestricted(packageName: String, restricted: Boolean): Boolean {
        return try {
            if (isProfileOwner() || isDeviceOwner()) {
                // Sử dụng addUserRestriction để chặn ứng dụng
                if (restricted) {
                    devicePolicyManager.addUserRestriction(componentName, 
                        "android.os.usertype.full.SYSTEM")
                } else {
                    devicePolicyManager.clearUserRestriction(componentName,
                        "android.os.usertype.full.SYSTEM")
                }

                // Sử dụng setApplicationHidden để ẩn ứng dụng
                devicePolicyManager.setApplicationHidden(componentName, packageName, restricted)

                // Sử dụng setPackagesSuspended để tạm dừng ứng dụng
                val packages = arrayOf(packageName)
                devicePolicyManager.setPackagesSuspended(componentName, packages, restricted)

                true
            } else {
                Log.e("DPC", "Not a profile or device owner")
                false
            }
        } catch (e: Exception) {
            Log.e("DPC", "Error setting application restriction", e)
            false
        }
    }

    fun isApplicationRestricted(packageName: String): Boolean {
        return try {
            if (isProfileOwner() || isDeviceOwner()) {
                devicePolicyManager.isApplicationHidden(componentName, packageName) ||
                devicePolicyManager.isPackageSuspended(componentName, packageName)
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("DPC", "Error checking application restriction", e)
            false
        }
    }
}
