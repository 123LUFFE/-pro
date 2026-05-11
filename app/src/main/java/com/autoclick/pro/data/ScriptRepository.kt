package com.autoclick.pro.data

import android.content.Context
import com.autoclick.pro.model.Script
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * 脚本数据仓库
 * 负责脚本的持久化存储和读取
 */
class ScriptRepository(private val context: Context) {
    
    private val gson = Gson()
    private val scriptsFile = File(context.filesDir, SCRIPTS_FILE_NAME)
    
    private val _scripts = MutableStateFlow<List<Script>>(emptyList())
    val scripts: StateFlow<List<Script>> = _scripts
    
    init {
        loadScripts()
    }
    
    /**
     * 加载所有脚本
     */
    private fun loadScripts() {
        try {
            if (scriptsFile.exists()) {
                val json = scriptsFile.readText()
                val type = object : TypeToken<List<Script>>() {}.type
                val loadedScripts: List<Script> = gson.fromJson(json, type)
                _scripts.value = loadedScripts
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _scripts.value = emptyList()
        }
    }
    
    /**
     * 保存所有脚本
     */
    private fun saveScripts() {
        try {
            val json = gson.toJson(_scripts.value)
            scriptsFile.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 添加脚本
     */
    fun addScript(script: Script): Boolean {
        val currentList = _scripts.value.toMutableList()
        currentList.add(0, script) // 添加到开头
        _scripts.value = currentList
        saveScripts()
        return true
    }
    
    /**
     * 更新脚本
     */
    fun updateScript(script: Script): Boolean {
        val currentList = _scripts.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == script.id }
        if (index != -1) {
            script.updateTimestamp()
            currentList[index] = script
            _scripts.value = currentList
            saveScripts()
            return true
        }
        return false
    }
    
    /**
     * 删除脚本
     */
    fun deleteScript(scriptId: String): Boolean {
        val currentList = _scripts.value.toMutableList()
        val removed = currentList.removeAll { it.id == scriptId }
        if (removed) {
            _scripts.value = currentList
            saveScripts()
            return true
        }
        return false
    }
    
    /**
     * 获取脚本
     */
    fun getScript(scriptId: String): Script? {
        return _scripts.value.find { it.id == scriptId }
    }
    
    /**
     * 复制脚本
     */
    fun duplicateScript(scriptId: String): Script? {
        val original = getScript(scriptId) ?: return null
        val copy = original.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = "${original.name} (副本)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        addScript(copy)
        return copy
    }
    
    /**
     * 重命名脚本
     */
    fun renameScript(scriptId: String, newName: String): Boolean {
        val script = getScript(scriptId) ?: return false
        script.name = newName
        script.updateTimestamp()
        return updateScript(script)
    }
    
    /**
     * 导出脚本为JSON
     */
    fun exportScript(scriptId: String): String? {
        val script = getScript(scriptId) ?: return null
        return gson.toJson(script)
    }
    
    /**
     * 导入脚本
     */
    fun importScript(json: String): Script? {
        return try {
            val script = gson.fromJson(json, Script::class.java)
            // 生成新ID避免冲突
            val newScript = script.copy(
                id = java.util.UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            addScript(newScript)
            newScript
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取脚本数量
     */
    fun getScriptCount(): Int = _scripts.value.size
    
    /**
     * 搜索脚本
     */
    fun searchScripts(query: String): List<Script> {
        if (query.isBlank()) return _scripts.value
        val lowerQuery = query.lowercase()
        return _scripts.value.filter { 
            it.name.lowercase().contains(lowerQuery) || 
            it.description.lowercase().contains(lowerQuery)
        }
    }
    
    /**
     * 按创建时间排序
     */
    fun sortByCreatedAt(ascending: Boolean = false): List<Script> {
        return if (ascending) {
            _scripts.value.sortedBy { it.createdAt }
        } else {
            _scripts.value.sortedByDescending { it.createdAt }
        }
    }
    
    /**
     * 按更新时间排序
     */
    fun sortByUpdatedAt(ascending: Boolean = false): List<Script> {
        return if (ascending) {
            _scripts.value.sortedBy { it.updatedAt }
        } else {
            _scripts.value.sortedByDescending { it.updatedAt }
        }
    }
    
    companion object {
        private const val SCRIPTS_FILE_NAME = "scripts.json"
        
        @Volatile
        private var instance: ScriptRepository? = null
        
        fun getInstance(context: Context): ScriptRepository {
            return instance ?: synchronized(this) {
                instance ?: ScriptRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
