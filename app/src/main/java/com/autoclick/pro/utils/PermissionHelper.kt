package com.autoclick.pro.utils

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.autoclick.pro.service.AutoClickAccessibilityService

/**
 * 权限检查和请求工具类
 */
object PermissionHelper {
    
    /**
     * 检查无障碍服务是否已启用
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return AutoClickAccessibilityService.isServiceEnabled()
    }
    
    /**
     * 检查浮动窗口权限是否已授予
     */
    fun isOverlayPermissionGranted(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }
    
    /**
     * 打开无障碍服务设置
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    
    /**
     * 打开浮动窗口权限设置
     */
    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    
    /**
     * 请求屏幕录制权限
     */
    fun requestScreenRecordPermission(activity: Activity, requestCode: Int) {
        val mediaProjectionManager = activity.getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        activity.startActivityForResult(intent, requestCode)
    }
    
    /**
     * 检查所有必要权限
     */
    fun checkAllPermissions(context: Context): Map<String, Boolean> {
        return mapOf(
            "accessibility" to isAccessibilityServiceEnabled(context),
            "overlay" to isOverlayPermissionGranted(context)
        )
    }
    
    /**
     * 获取缺失的权限列表
     */
    fun getMissingPermissions(context: Context): List<String> {
        val missing = mutableListOf<String>()
        
        if (!isAccessibilityServiceEnabled(context)) {
            missing.add("accessibility")
        }
        
        if (!isOverlayPermissionGranted(context)) {
            missing.add("overlay")
        }
        
        return missing
    }
    
    /**
     * 是否所有必要权限都已授予
     */
    fun hasAllPermissions(context: Context): Boolean {
        return getMissingPermissions(context).isEmpty()
    }
}
