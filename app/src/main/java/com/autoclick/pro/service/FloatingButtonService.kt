package com.autoclick.pro.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.autoclick.pro.R
import com.autoclick.pro.model.Script
import com.autoclick.pro.ui.MainActivity
import com.autoclick.pro.utils.PreferenceManager
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 浮动按钮服务
 * 显示可拖动、可调整大小的浮动控制按钮
 */
class FloatingButtonService : Service() {

    companion object {
        const val CHANNEL_ID = "floating_button_channel"
        const val NOTIFICATION_ID = 1002
        
        const val ACTION_SHOW_BUTTON = "com.autoclick.pro.SHOW_BUTTON"
        const val ACTION_HIDE_BUTTON = "com.autoclick.pro.HIDE_BUTTON"
        const val ACTION_UPDATE_SCRIPT = "com.autoclick.pro.UPDATE_SCRIPT"
        const val ACTION_UPDATE_SIZE = "com.autoclick.pro.UPDATE_SIZE"
        const val ACTION_UPDATE_SPEED = "com.autoclick.pro.UPDATE_SPEED"
        
        const val EXTRA_SCRIPT = "script"
        const val EXTRA_SIZE = "size"
        const val EXTRA_SPEED = "speed"
        
        // 状态流
        private val _isShowing = MutableStateFlow(false)
        val isShowing: StateFlow<Boolean> = _isShowing
        
        // 当前脚本
        private val _currentScript = MutableStateFlow<Script?>(null)
        val currentScript: StateFlow<Script?> = _currentScript
        
        // 是否正在执行
        private val _isPlaying = MutableStateFlow(false)
        val isPlaying: StateFlow<Boolean> = _isPlaying
    }
    
    private lateinit var windowManager: WindowManager
    private lateinit var preferenceManager: PreferenceManager
    private var floatingView: View? = null
    private var controlPanelView: View? = null
    
    private var buttonSize: Int = 60 // dp
    private var playbackSpeed: Float = 1.0f
    private var currentScriptData: Script? = null
    
    private val scope = CoroutineScope(Dispatchers.Main)
    private val gson = Gson()
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        preferenceManager = PreferenceManager(this)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_BUTTON -> {
                val scriptJson = intent.getStringExtra(EXTRA_SCRIPT)
                scriptJson?.let {
                    currentScriptData = gson.fromJson(it, Script::class.java)
                    _currentScript.value = currentScriptData
                }
                showFloatingButton()
            }
            ACTION_HIDE_BUTTON -> hideFloatingButton()
            ACTION_UPDATE_SCRIPT -> {
                val scriptJson = intent.getStringExtra(EXTRA_SCRIPT)
                scriptJson?.let {
                    currentScriptData = gson.fromJson(it, Script::class.java)
                    _currentScript.value = currentScriptData
                }
            }
            ACTION_UPDATE_SIZE -> {
                buttonSize = intent.getIntExtra(EXTRA_SIZE, 60)
                updateButtonSize()
            }
            ACTION_UPDATE_SPEED -> {
                playbackSpeed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.floating_button))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun showFloatingButton() {
        if (floatingView != null) return
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 获取默认设置
        buttonSize = preferenceManager.defaultButtonSize
        playbackSpeed = preferenceManager.defaultSpeed
        
        // 创建浮动按钮
        floatingView = createFloatingButtonView()
        
        val params = createButtonLayoutParams()
        windowManager.addView(floatingView, params)
        
        _isShowing.value = true
    }
    
    @SuppressLint("InflateParams")
    private fun createFloatingButtonView(): View {
        val container = FrameLayout(this)
        
        // 创建主按钮
        val button = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundResource(R.drawable.floating_button_bg)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(16, 16, 16, 16)
            
            setOnClickListener {
                togglePlayback()
            }
            
            setOnLongClickListener {
                showControlPanel()
                true
            }
        }
        
        container.addView(button, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        
        // 设置触摸监听实现拖动
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var lastClickTime = 0L
        
        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = (floatingView?.layoutParams as WindowManager.LayoutParams).x
                    initialY = (floatingView?.layoutParams as WindowManager.LayoutParams).y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    lastClickTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    
                    // 只有移动距离超过阈值才认为是拖动
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        val params = floatingView?.layoutParams as? WindowManager.LayoutParams
                        params?.let {
                            it.x = initialX + deltaX
                            it.y = initialY + deltaY
                            windowManager.updateViewLayout(floatingView, it)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val clickDuration = System.currentTimeMillis() - lastClickTime
                    val deltaX = Math.abs(event.rawX - initialTouchX).toInt()
                    val deltaY = Math.abs(event.rawY - initialTouchY).toInt()
                    
                    // 如果是点击（非拖动）
                    if (clickDuration < 200 && deltaX < 10 && deltaY < 10) {
                        togglePlayback()
                    }
                    true
                }
                else -> false
            }
        }
        
        return container
    }
    
    private fun createButtonLayoutParams(): WindowManager.LayoutParams {
        val sizePx = (buttonSize * resources.displayMetrics.density).toInt()
        
        return WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = 100
            y = 200
        }
    }
    
    private fun updateButtonSize() {
        floatingView?.let { view ->
            val params = view.layoutParams as? WindowManager.LayoutParams
            params?.let {
                val sizePx = (buttonSize * resources.displayMetrics.density).toInt()
                it.width = sizePx
                it.height = sizePx
                windowManager.updateViewLayout(view, it)
            }
        }
    }
    
    private fun togglePlayback() {
        val accessibilityService = AutoClickAccessibilityService.getInstance()
        if (accessibilityService == null) {
            // 提示用户开启无障碍服务
            return
        }
        
        if (_isPlaying.value) {
            // 停止执行
            accessibilityService.stopExecution()
            _isPlaying.value = false
            updateButtonIcon(false)
        } else {
            // 开始执行
            currentScriptData?.let { script ->
                accessibilityService.executeActions(
                    actions = script.actions,
                    speed = playbackSpeed,
                    loopCount = script.loopCount,
                    loopDelay = script.loopDelay,
                    onComplete = {
                        _isPlaying.value = false
                        updateButtonIcon(false)
                    },
                    onError = {
                        _isPlaying.value = false
                        updateButtonIcon(false)
                    }
                )
                _isPlaying.value = true
                updateButtonIcon(true)
            }
        }
    }
    
    private fun updateButtonIcon(isPlaying: Boolean) {
        floatingView?.let { view ->
            val button = view.findViewById<ImageButton>(android.R.id.icon)
                ?: (view as? FrameLayout)?.getChildAt(0) as? ImageButton
            button?.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
        }
    }
    
    @SuppressLint("InflateParams")
    private fun showControlPanel() {
        if (controlPanelView != null) {
            windowManager.removeView(controlPanelView)
            controlPanelView = null
            return
        }
        
        // 创建控制面板
        controlPanelView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xDD000000.toInt())
            setPadding(24, 24, 24, 24)
            
            // 标题
            addView(TextView(this@FloatingButtonService).apply {
                text = getString(R.string.floating_button)
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 18f
                gravity = Gravity.CENTER
            })
            
            // 按钮大小调节
            addView(TextView(this@FloatingButtonService).apply {
                text = getString(R.string.button_size)
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, 16, 0, 8)
            })
            
            addView(SeekBar(this@FloatingButtonService).apply {
                max = 100
                progress = buttonSize
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        buttonSize = progress.coerceAtLeast(30)
                        updateButtonSize()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            })
            
            // 执行速度调节
            addView(TextView(this@FloatingButtonService).apply {
                text = getString(R.string.playback_speed)
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, 16, 0, 8)
            })
            
            addView(SeekBar(this@FloatingButtonService).apply {
                max = 30 // 0.1x - 3.0x
                progress = (playbackSpeed * 10).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        playbackSpeed = (progress.coerceAtLeast(1)) / 10f
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            })
            
            // 关闭按钮
            addView(android.widget.Button(this@FloatingButtonService).apply {
                text = getString(R.string.cancel)
                setOnClickListener {
                    hideControlPanel()
                }
            })
        }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        
        windowManager.addView(controlPanelView, params)
        
        // 点击外部关闭
        controlPanelView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                hideControlPanel()
                true
            } else {
                false
            }
        }
    }
    
    private fun hideControlPanel() {
        controlPanelView?.let {
            windowManager.removeView(it)
            controlPanelView = null
        }
    }
    
    private fun hideFloatingButton() {
        hideControlPanel()
        
        floatingView?.let {
            windowManager.removeView(it)
            floatingView = null
        }
        
        _isShowing.value = false
        _isPlaying.value = false
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        hideFloatingButton()
        _isShowing.value = false
    }
}
