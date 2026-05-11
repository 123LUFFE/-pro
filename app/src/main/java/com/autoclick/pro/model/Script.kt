package com.autoclick.pro.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * 脚本数据类
 */
data class Script(
    @SerializedName("id")
    val id: String = UUID.randomUUID().toString(),
    
    @SerializedName("name")
    var name: String,
    
    @SerializedName("description")
    var description: String = "",
    
    @SerializedName("actions")
    val actions: MutableList<Action> = mutableListOf(),
    
    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis(),
    
    @SerializedName("updatedAt")
    var updatedAt: Long = System.currentTimeMillis(),
    
    @SerializedName("loopCount")
    var loopCount: Int = 1, // 循环次数，0表示无限循环
    
    @SerializedName("loopDelay")
    var loopDelay: Long = 1000, // 循环间隔（毫秒）
    
    @SerializedName("playbackSpeed")
    var playbackSpeed: Float = 1.0f // 执行速度倍率
) {
    fun updateTimestamp() {
        updatedAt = System.currentTimeMillis()
    }
}

/**
 * 操作类型枚举
 */
enum class ActionType {
    CLICK,          // 单击
    LONG_CLICK,     // 长按
    SWIPE,          // 滑动
    WAIT,           // 等待
    LOOP_START,     // 循环开始
    LOOP_END,       // 循环结束
    CONDITION,      // 条件判断
    GESTURE         // 手势
}

/**
 * 操作数据类
 */
data class Action(
    @SerializedName("id")
    val id: String = UUID.randomUUID().toString(),
    
    @SerializedName("type")
    val type: ActionType,
    
    @SerializedName("x")
    var x: Float = 0f,
    
    @SerializedName("y")
    var y: Float = 0f,
    
    @SerializedName("endX")
    var endX: Float = 0f, // 滑动终点X
    
    @SerializedName("endY")
    var endY: Float = 0f, // 滑动终点Y
    
    @SerializedName("duration")
    var duration: Long = 100, // 操作持续时间（毫秒）
    
    @SerializedName("delay")
    var delay: Long = 0, // 操作后延迟（毫秒）
    
    @SerializedName("loopCount")
    var loopCount: Int = 1, // 循环次数（仅LOOP_START类型有效）
    
    @SerializedName("conditionType")
    var conditionType: ConditionType? = null, // 条件类型
    
    @SerializedName("conditionValue")
    var conditionValue: String = "", // 条件值
    
    @SerializedName("gesturePath")
    var gesturePath: List<Point>? = null, // 手势路径
    
    @SerializedName("enabled")
    var enabled: Boolean = true // 是否启用
)

/**
 * 条件类型枚举
 */
enum class ConditionType {
    COLOR_MATCH,    // 颜色匹配
    IMAGE_MATCH,    // 图片匹配
    TEXT_MATCH,     // 文字匹配
    TIME_RANGE      // 时间范围
}

/**
 * 点坐标
 */
data class Point(
    @SerializedName("x")
    val x: Float,
    
    @SerializedName("y")
    val y: Float,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 录制状态
 */
enum class RecordingState {
    IDLE,       // 空闲
    RECORDING,  // 录制中
    PAUSED      // 已暂停
}

/**
 * 回放状态
 */
enum class PlaybackState {
    IDLE,       // 空闲
    PLAYING,    // 执行中
    PAUSED,     // 已暂停
    COMPLETED   // 已完成
}
