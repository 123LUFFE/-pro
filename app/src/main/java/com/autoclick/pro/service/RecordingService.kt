package com.autoclick.pro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import com.autoclick.pro.R
import com.autoclick.pro.model.Action
import com.autoclick.pro.model.ActionType
import com.autoclick.pro.model.Point
import com.autoclick.pro.model.RecordingState
import com.autoclick.pro.utils.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 录制服务
 * 负责录制用户的触摸操作
 */
class RecordingService : Service() {

    companion object {
        const val CHANNEL_ID = "recording_channel"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_START_RECORDING = "com.autoclick.pro.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.autoclick.pro.STOP_RECORDING"
        const val ACTION_PAUSE_RECORDING = "com.autoclick.pro.PAUSE_RECORDING"
        const val ACTION_RESUME_RECORDING = "com.autoclick.pro.RESUME_RECORDING"
        
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        
        // 状态流
        private val _recordingState = MutableStateFlow(RecordingState.IDLE)
        val recordingState: StateFlow<RecordingState> = _recordingState
        
        // 录制的操作列表
        private val _recordedActions = MutableStateFlow<List<Action>>(emptyList())
        val recordedActions: StateFlow<List<Action>> = _recordedActions
        
        // 当前录制是否活跃
        var isRecording: Boolean = false
            private set
    }
    
    private lateinit var windowManager: WindowManager
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var overlayView: View? = null
    
    private val recordedActionsList = mutableListOf<Action>()
    private var lastActionTime: Long = 0
    private var lastPoint: Point? = null
    private var isSwipping = false
    private val swipePoints = mutableListOf<Point>()
    
    private val scope = CoroutineScope(Dispatchers.Default)
    private lateinit var preferenceManager: PreferenceManager
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        preferenceManager = PreferenceManager(this)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, android.content.Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                startRecording(resultCode, resultData)
            }
            ACTION_STOP_RECORDING -> stopRecording()
            ACTION_PAUSE_RECORDING -> pauseRecording()
            ACTION_RESUME_RECORDING -> resumeRecording()
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
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_recording))
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setOngoing(true)
            .build()
    }
    
    private fun startRecording(resultCode: Int, resultData: Intent?) {
        if (resultCode == -1 || resultData == null) {
            stopSelf()
            return
        }
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 初始化MediaProjection
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
        
        // 清空之前的录制
        recordedActionsList.clear()
        lastActionTime = 0
        lastPoint = null
        isSwipping = false
        swipePoints.clear()
        
        // 创建触摸覆盖层
        createTouchOverlay()
        
        isRecording = true
        _recordingState.value = RecordingState.RECORDING
        _recordedActions.value = emptyList()
    }
    
    private fun createTouchOverlay() {
        // 创建全屏透明覆盖层来捕获触摸事件
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
        }
        
        overlayView = object : FrameLayout(this) {
            override fun onTouchEvent(event: MotionEvent?): Boolean {
                event?.let { handleTouchEvent(it) }
                return true
            }
            
            override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
                ev?.let { handleTouchEvent(it) }
                return false // 不拦截，让事件传递到下层
            }
        }.apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        
        windowManager.addView(overlayView, params)
    }
    
    private fun handleTouchEvent(event: MotionEvent) {
        if (_recordingState.value != RecordingState.RECORDING) return
        
        val currentTime = System.currentTimeMillis()
        val x = event.x
        val y = event.y
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 记录按下时间和位置
                lastActionTime = currentTime
                lastPoint = Point(x, y, currentTime)
                isSwipping = false
                swipePoints.clear()
                swipePoints.add(Point(x, y, currentTime))
            }
            
            MotionEvent.ACTION_MOVE -> {
                // 记录滑动轨迹
                isSwipping = true
                swipePoints.add(Point(x, y, currentTime))
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val duration = currentTime - lastActionTime
                val delay = if (recordedActionsList.isEmpty()) 0L else {
                    currentTime - (recordedActionsList.last().let { 
                        it.delay + lastActionTime 
                    })
                }
                
                val action = if (isSwipping && swipePoints.size > 2) {
                    // 滑动操作
                    val startPoint = swipePoints.first()
                    val endPoint = swipePoints.last()
                    Action(
                        type = ActionType.SWIPE,
                        x = startPoint.x,
                        y = startPoint.y,
                        endX = endPoint.x,
                        endY = endPoint.y,
                        duration = duration,
                        delay = delay,
                        gesturePath = swipePoints.toList()
                    )
                } else if (duration > 500) {
                    // 长按操作
                    Action(
                        type = ActionType.LONG_CLICK,
                        x = x,
                        y = y,
                        duration = duration,
                        delay = delay
                    )
                } else {
                    // 单击操作
                    Action(
                        type = ActionType.CLICK,
                        x = x,
                        y = y,
                        duration = duration,
                        delay = delay
                    )
                }
                
                recordedActionsList.add(action)
                _recordedActions.value = recordedActionsList.toList()
                
                // 重置状态
                isSwipping = false
                swipePoints.clear()
                lastPoint = null
                
                // 震动反馈
                if (preferenceManager.isVibrationEnabled) {
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(50)
                    }
                }
            }
        }
    }
    
    private fun stopRecording() {
        isRecording = false
        _recordingState.value = RecordingState.IDLE
        
        // 移除覆盖层
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
        
        // 释放MediaProjection
        mediaProjection?.stop()
        mediaProjection = null
        
        // 停止前台服务
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun pauseRecording() {
        _recordingState.value = RecordingState.PAUSED
    }
    
    private fun resumeRecording() {
        _recordingState.value = RecordingState.RECORDING
    }
    
    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let {
            windowManager.removeView(it)
        }
        mediaProjection?.stop()
        isRecording = false
        _recordingState.value = RecordingState.IDLE
    }
}
