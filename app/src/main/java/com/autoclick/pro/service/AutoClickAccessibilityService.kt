package com.autoclick.pro.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.autoclick.pro.model.Action
import com.autoclick.pro.model.ActionType
import com.autoclick.pro.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 无障碍服务
 * 负责执行手势操作（点击、滑动、长按等）
 */
class AutoClickAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: AutoClickAccessibilityService? = null
        
        fun getInstance(): AutoClickAccessibilityService? = instance
        
        fun isServiceEnabled(): Boolean = instance != null
        
        // 状态流
        private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
        val playbackState: StateFlow<PlaybackState> = _playbackState
        
        // 当前执行的脚本ID
        private var currentScriptId: String? = null
        
        // 是否正在执行
        var isPlaying: Boolean = false
            private set
        
        // 停止标志
        private var shouldStop = false
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default)
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要处理事件
    }
    
    override fun onInterrupt() {
        // 服务中断
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isPlaying = false
        _playbackState.value = PlaybackState.IDLE
    }
    
    /**
     * 执行单击操作
     */
    fun performClick(x: Float, y: Float, duration: Long = 100): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return dispatchGesture(gesture, null, null)
    }
    
    /**
     * 执行长按操作
     */
    fun performLongClick(x: Float, y: Float, duration: Long = 500): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return dispatchGesture(gesture, null, null)
    }
    
    /**
     * 执行滑动操作
     */
    fun performSwipe(
        startX: Float, 
        startY: Float, 
        endX: Float, 
        endY: Float, 
        duration: Long = 300
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return dispatchGesture(gesture, null, null)
    }
    
    /**
     * 执行手势路径
     */
    fun performGesture(
        points: List<com.autoclick.pro.model.Point>,
        duration: Long
    ): Boolean {
        if (points.isEmpty()) return false
        
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return dispatchGesture(gesture, null, null)
    }
    
    /**
     * 执行多指手势
     */
    fun performMultiTouch(
        paths: List<Path>,
        startTime: Long,
        duration: Long
    ): Boolean {
        val builder = GestureDescription.Builder()
        paths.forEach { path ->
            builder.addStroke(GestureDescription.StrokeDescription(path, startTime, duration))
        }
        return dispatchGesture(builder.build(), null, null)
    }
    
    /**
     * 执行操作列表
     */
    fun executeActions(
        actions: List<Action>,
        speed: Float = 1.0f,
        loopCount: Int = 1,
        loopDelay: Long = 1000,
        onProgress: ((Int, Int) -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (isPlaying) {
            onError?.invoke("已有脚本正在执行")
            return
        }
        
        isPlaying = true
        shouldStop = false
        _playbackState.value = PlaybackState.PLAYING
        
        scope.launch(Dispatchers.Main) {
            try {
                val totalLoops = if (loopCount == 0) Int.MAX_VALUE else loopCount
                var currentLoop = 0
                
                while (currentLoop < totalLoops && !shouldStop) {
                    // 处理循环结构
                    val loopStack = mutableListOf<Int>()
                    val loopCounters = mutableMapOf<Int, Int>()
                    
                    var actionIndex = 0
                    while (actionIndex < actions.size && !shouldStop) {
                        val action = actions[actionIndex]
                        
                        if (!action.enabled) {
                            actionIndex++
                            continue
                        }
                        
                        when (action.type) {
                            ActionType.LOOP_START -> {
                                loopStack.push(actionIndex)
                                loopCounters[actionIndex] = action.loopCount
                                actionIndex++
                            }
                            
                            ActionType.LOOP_END -> {
                                if (loopStack.isNotEmpty()) {
                                    val startIndex = loopStack.peek()
                                    val counter = loopCounters[startIndex] ?: 1
                                    
                                    if (counter > 1) {
                                        loopCounters[startIndex] = counter - 1
                                        actionIndex = startIndex + 1
                                    } else {
                                        loopStack.pop()
                                        loopCounters.remove(startIndex)
                                        actionIndex++
                                    }
                                } else {
                                    actionIndex++
                                }
                            }
                            
                            ActionType.CONDITION -> {
                                // 条件判断逻辑
                                // TODO: 实现条件判断
                                actionIndex++
                            }
                            
                            else -> {
                                // 执行普通操作
                                val adjustedDelay = (action.delay / speed).toLong()
                                val adjustedDuration = (action.duration / speed).toLong()
                                
                                if (adjustedDelay > 0) {
                                    Thread.sleep(adjustedDelay)
                                }
                                
                                if (shouldStop) break
                                
                                val success = executeAction(action, adjustedDuration)
                                
                                if (!success) {
                                    onError?.invoke("操作执行失败: ${action.type}")
                                }
                                
                                onProgress?.invoke(actionIndex, actions.size)
                                actionIndex++
                            }
                        }
                    }
                    
                    currentLoop++
                    
                    if (currentLoop < totalLoops && !shouldStop && loopDelay > 0) {
                        Thread.sleep(loopDelay)
                    }
                }
                
                _playbackState.value = if (shouldStop) PlaybackState.IDLE else PlaybackState.COMPLETED
                onComplete?.invoke()
                
            } catch (e: Exception) {
                _playbackState.value = PlaybackState.IDLE
                onError?.invoke("执行出错: ${e.message}")
            } finally {
                isPlaying = false
            }
        }
    }
    
    /**
     * 执行单个操作
     */
    private fun executeAction(action: Action, duration: Long): Boolean {
        return when (action.type) {
            ActionType.CLICK -> performClick(action.x, action.y, duration.coerceAtLeast(10))
            ActionType.LONG_CLICK -> performLongClick(action.x, action.y, duration.coerceAtLeast(100))
            ActionType.SWIPE -> {
                if (action.gesturePath != null && action.gesturePath!!.size > 2) {
                    performGesture(action.gesturePath!!, duration.coerceAtLeast(50))
                } else {
                    performSwipe(action.x, action.y, action.endX, action.endY, duration.coerceAtLeast(50))
                }
            }
            ActionType.WAIT -> {
                Thread.sleep(duration.coerceAtLeast(0))
                true
            }
            ActionType.GESTURE -> {
                if (action.gesturePath != null) {
                    performGesture(action.gesturePath!!, duration.coerceAtLeast(50))
                } else {
                    false
                }
            }
            else -> true // LOOP_START, LOOP_END, CONDITION 在外层处理
        }
    }
    
    /**
     * 停止执行
     */
    fun stopExecution() {
        shouldStop = true
        isPlaying = false
        _playbackState.value = PlaybackState.IDLE
    }
    
    /**
     * 暂停执行
     */
    fun pauseExecution() {
        _playbackState.value = PlaybackState.PAUSED
    }
    
    /**
     * 恢复执行
     */
    fun resumeExecution() {
        _playbackState.value = PlaybackState.PLAYING
    }
    
    /**
     * 辅助扩展函数
     */
    private fun <T> MutableList<T>.push(element: T) = add(element)
    private fun <T> MutableList<T>.pop(): T = removeAt(size - 1)
    private fun <T> MutableList<T>.peek(): T = get(size - 1)
}
