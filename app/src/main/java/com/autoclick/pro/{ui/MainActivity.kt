package com.autoclick.pro.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.autoclick.pro.R
import com.autoclick.pro.data.ScriptRepository
import com.autoclick.pro.databinding.ActivityMainBinding
import com.autoclick.pro.model.Action
import com.autoclick.pro.model.RecordingState
import com.autoclick.pro.model.Script
import com.autoclick.pro.service.AutoClickAccessibilityService
import com.autoclick.pro.service.FloatingButtonService
import com.autoclick.pro.service.RecordingService
import com.autoclick.pro.utils.PermissionHelper
import com.autoclick.pro.utils.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 主Activity
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var scriptRepository: ScriptRepository
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var scriptsAdapter: ScriptsAdapter
    private val gson = Gson()
    
    private var isRecording = false
    private var currentPermissionType: PermissionType? = null
    
    private enum class PermissionType {
        ACCESSIBILITY, OVERLAY, SCREEN_RECORD
    }
    
    // 屏幕录制请求
    private val screenRecordLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startRecordingService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, R.string.recording_failed, Toast.LENGTH_SHORT).show()
        }
    }
    
    // 无障碍服务设置
    private val accessibilitySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkPermissions()
    }
    
    // 浮动窗口权限
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 初始化
        scriptRepository = ScriptRepository.getInstance(this)
        preferenceManager = PreferenceManager(this)
        
        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeData()
        
        // 检查权限
        checkPermissions()
    }
    
    override fun onResume() {
        super.onResume()
        checkPermissions()
        updateRecordingState()
    }
    
    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
        
        // 搜索功能
        val searchItem = binding.toolbar.menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                scriptsAdapter.filter(newText ?: "")
                return true
            }
        })
    }
    
    private fun setupRecyclerView() {
        scriptsAdapter = ScriptsAdapter(
            onPlayClick = { script -> executeScript(script) },
            onFloatingClick = { script -> showFloatingButton(script) },
            onEditClick = { script -> editScript(script) },
            onDeleteClick = { script -> confirmDeleteScript(script) },
            onDuplicateClick = { script -> duplicateScript(script) }
        )
        
        binding.scriptsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = scriptsAdapter
        }
    }
    
    private fun setupListeners() {
        // 录制按钮
        binding.recordFab.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
        
        // 停止录制按钮
        binding.stopRecordButton.setOnClickListener {
            stopRecording()
        }
        
        // 权限按钮
        binding.permissionButton.setOnClickListener {
            currentPermissionType?.let { type ->
                when (type) {
                    PermissionType.ACCESSIBILITY -> openAccessibilitySettings()
                    PermissionType.OVERLAY -> requestOverlayPermission()
                    PermissionType.SCREEN_RECORD -> { /* 屏幕录制权限在录制时请求 */ }
                }
            }
        }
        
        // 下拉刷新
        binding.swipeRefresh.setOnRefreshListener {
            scriptsAdapter.updateScripts(scriptRepository.scripts.value)
            binding.swipeRefresh.isRefreshing = false
        }
    }
    
    private fun observeData() {
        // 观察脚本列表
        lifecycleScope.launch {
            scriptRepository.scripts.collectLatest { scripts ->
                scriptsAdapter.updateScripts(scripts)
                binding.emptyState.visibility = if (scripts.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        
        // 观察录制状态
        lifecycleScope.launch {
            RecordingService.recordingState.collectLatest { state ->
                isRecording = state == RecordingState.RECORDING
                updateRecordingUI()
            }
        }
        
        // 观察录制结果
        lifecycleScope.launch {
            RecordingService.recordedActions.collectLatest { actions ->
                if (actions.isNotEmpty() && !isRecording) {
                    showSaveScriptDialog(actions)
                }
            }
        }
    }
    
    private fun checkPermissions() {
        val accessibilityEnabled = AutoClickAccessibilityService.isServiceEnabled()
        val overlayEnabled = Settings.canDrawOverlays(this)
        
        when {
            !accessibilityEnabled -> {
                currentPermissionType = PermissionType.ACCESSIBILITY
                showPermissionCard(getString(R.string.permission_accessibility_desc))
            }
            !overlayEnabled -> {
                currentPermissionType = PermissionType.OVERLAY
                showPermissionCard(getString(R.string.permission_overlay_desc))
            }
            else -> {
                binding.permissionCard.visibility = View.GONE
            }
        }
    }
    
    private fun showPermissionCard(message: String) {
        binding.permissionCard.visibility = View.VISIBLE
        binding.permissionText.text = message
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        accessibilitySettingsLauncher.launch(intent)
    }
    
    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }
    
    private fun startRecording() {
        if (!AutoClickAccessibilityService.isServiceEnabled()) {
            Toast.makeText(this, R.string.permission_accessibility_desc, Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }
        
        // 请求屏幕录制权限
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenRecordLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }
    
    private fun startRecordingService(resultCode: Int, data: Intent) {
        val intent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START_RECORDING
            putExtra(RecordingService.EXTRA_RESULT_CODE, resultCode)
            putExtra(RecordingService.EXTRA_RESULT_DATA, data)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        isRecording = true
        updateRecordingUI()
        
        Toast.makeText(this, R.string.start_recording, Toast.LENGTH_SHORT).show()
    }
    
    private fun stopRecording() {
        val intent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP_RECORDING
        }
        startService(intent)
        
        isRecording = false
        updateRecordingUI()
    }
    
    private fun updateRecordingUI() {
        if (isRecording) {
            binding.recordFab.text = getString(R.string.stop_recording)
            binding.recordFab.setIconResource(android.R.drawable.ic_media_pause)
            binding.recordingIndicator.visibility = View.VISIBLE
        } else {
            binding.recordFab.text = getString(R.string.start_recording)
            binding.recordFab.setIconResource(R.drawable.ic_record)
            binding.recordingIndicator.visibility = View.GONE
        }
    }
    
    private fun updateRecordingState() {
        isRecording = RecordingService.isRecording
        updateRecordingUI()
    }
    
    private fun showSaveScriptDialog(actions: List<Action>) {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.script_name)
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.recording_saved)
            .setMessage("录制的操作数量: ${actions.size}")
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().ifBlank { 
                    "脚本 ${System.currentTimeMillis()}" 
                }
                val script = Script(name = name, actions = actions.toMutableList())
                scriptRepository.addScript(script)
                Toast.makeText(this, R.string.recording_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun executeScript(script: Script) {
        val accessibilityService = AutoClickAccessibilityService.getInstance()
        if (accessibilityService == null) {
            Toast.makeText(this, R.string.permission_accessibility_desc, Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }
        
        accessibilityService.executeActions(
            actions = script.actions,
            speed = script.playbackSpeed,
            loopCount = script.loopCount,
            loopDelay = script.loopDelay,
            onComplete = {
                runOnUiThread {
                    Toast.makeText(this, R.string.playback_completed, Toast.LENGTH_SHORT).show()
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                }
            }
        )
        
        Toast.makeText(this, R.string.start_playback, Toast.LENGTH_SHORT).show()
    }
    
    private fun showFloatingButton(script: Script) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.permission_overlay_desc, Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }
        
        val scriptJson = gson.toJson(script)
        val intent = Intent(this, FloatingButtonService::class.java).apply {
            action = FloatingButtonService.ACTION_SHOW_BUTTON
            putExtra(FloatingButtonService.EXTRA_SCRIPT, scriptJson)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        Toast.makeText(this, R.string.floating_button, Toast.LENGTH_SHORT).show()
    }
    
    private fun editScript(script: Script) {
        val intent = Intent(this, ScriptEditActivity::class.java).apply {
            putExtra(ScriptEditActivity.EXTRA_SCRIPT_ID, script.id)
        }
        startActivity(intent)
    }
    
    private fun confirmDeleteScript(script: Script) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage("确定要删除 \"${script.name}\" 吗？")
            .setPositiveButton(R.string.delete) { _, _ ->
                scriptRepository.deleteScript(script.id)
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun duplicateScript(script: Script) {
        val copy = scriptRepository.duplicateScript(script.id)
        if (copy != null) {
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
}
