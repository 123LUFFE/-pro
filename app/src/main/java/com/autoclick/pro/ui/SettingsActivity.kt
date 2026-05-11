package com.autoclick.pro.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.SeekBarPreference
import com.autoclick.pro.R
import com.autoclick.pro.service.AutoClickAccessibilityService
import com.autoclick.pro.utils.PreferenceManager

/**
 * 设置Activity
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settingsContainer, SettingsFragment())
            .commit()
        
        // 设置返回按钮
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)?.setNavigationOnClickListener {
            finish()
        }
    }
    
    class SettingsFragment : PreferenceFragmentCompat() {
        
        private lateinit var preferenceManager: PreferenceManager
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager = PreferenceManager(requireContext())
            setPreferencesFromResource(R.xml.preferences, rootKey)
            
            setupPreferences()
        }
        
        private fun setupPreferences() {
            // 无障碍服务状态
            findPreference<SwitchPreferenceCompat>("accessibility_service")?.apply {
                isChecked = AutoClickAccessibilityService.isServiceEnabled()
                setOnPreferenceClickListener {
                    if (!AutoClickAccessibilityService.isServiceEnabled()) {
                        openAccessibilitySettings()
                    }
                    true
                }
            }
            
            // 浮动窗口权限
            findPreference<SwitchPreferenceCompat>("overlay_permission")?.apply {
                isChecked = Settings.canDrawOverlays(requireContext())
                setOnPreferenceClickListener {
                    if (!Settings.canDrawOverlays(requireContext())) {
                        openOverlaySettings()
                    }
                    true
                }
            }
            
            // 默认执行速度
            findPreference<SeekBarPreference>("default_speed")?.apply {
                value = (preferenceManager.defaultSpeed * 10).toInt()
                setOnPreferenceChangeListener { _, newValue ->
                    preferenceManager.defaultSpeed = (newValue as Int) / 10f
                    true
                }
            }
            
            // 默认按钮大小
            findPreference<SeekBarPreference>("default_button_size")?.apply {
                value = preferenceManager.defaultButtonSize
                setOnPreferenceChangeListener { _, newValue ->
                    preferenceManager.defaultButtonSize = newValue as Int
                    true
                }
            }
            
            // 震动反馈
            findPreference<SwitchPreferenceCompat>("vibration")?.apply {
                isChecked = preferenceManager.isVibrationEnabled
                setOnPreferenceChangeListener { _, newValue ->
                    preferenceManager.isVibrationEnabled = newValue as Boolean
                    true
                }
            }
            
            // 显示通知
            findPreference<SwitchPreferenceCompat>("notification")?.apply {
                isChecked = preferenceManager.isNotificationEnabled
                setOnPreferenceChangeListener { _, newValue ->
                    preferenceManager.isNotificationEnabled = newValue as Boolean
                    true
                }
            }
        }
        
        private fun openAccessibilitySettings() {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
        
        private fun openOverlaySettings() {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")
            )
            startActivity(intent)
        }
        
        override fun onResume() {
            super.onResume()
            setupPreferences()
        }
    }
}
