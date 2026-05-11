package com.autoclick.pro.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * 偏好设置管理器
 */
class PreferenceManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    // 默认执行速度
    var defaultSpeed: Float
        get() = prefs.getFloat(KEY_DEFAULT_SPEED, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_DEFAULT_SPEED, value).apply()
    
    // 默认按钮大小 (dp)
    var defaultButtonSize: Int
        get() = prefs.getInt(KEY_DEFAULT_BUTTON_SIZE, 60)
        set(value) = prefs.edit().putInt(KEY_DEFAULT_BUTTON_SIZE, value).apply()
    
    // 震动反馈
    var isVibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION, value).apply()
    
    // 显示通知
    var isNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION, value).apply()
    
    // 默认循环次数
    var defaultLoopCount: Int
        get() = prefs.getInt(KEY_LOOP_COUNT, 1)
        set(value) = prefs.edit().putInt(KEY_LOOP_COUNT, value).apply()
    
    // 默认循环间隔
    var defaultLoopDelay: Long
        get() = prefs.getLong(KEY_LOOP_DELAY, 1000)
        set(value) = prefs.edit().putLong(KEY_LOOP_DELAY, value).apply()
    
    // 最近使用的脚本ID
    var lastScriptId: String?
        get() = prefs.getString(KEY_LAST_SCRIPT_ID, null)
        set(value) = prefs.edit().putString(KEY_LAST_SCRIPT_ID, value).apply()
    
    // 是否首次启动
    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()
    
    // 是否已显示权限引导
    var hasShownPermissionGuide: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_GUIDE, false)
        set(value) = prefs.edit().putBoolean(KEY_PERMISSION_GUIDE, value).apply()
    
    companion object {
        private const val PREFS_NAME = "auto_click_prefs"
        
        private const val KEY_DEFAULT_SPEED = "default_speed"
        private const val KEY_DEFAULT_BUTTON_SIZE = "default_button_size"
        private const val KEY_VIBRATION = "vibration"
        private const val KEY_NOTIFICATION = "notification"
        private const val KEY_LOOP_COUNT = "loop_count"
        private const val KEY_LOOP_DELAY = "loop_delay"
        private const val KEY_LAST_SCRIPT_ID = "last_script_id"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_PERMISSION_GUIDE = "permission_guide"
    }
}
