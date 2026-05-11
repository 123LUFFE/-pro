package com.autoclick.pro.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.autoclick.pro.R
import com.autoclick.pro.data.ScriptRepository
import com.autoclick.pro.databinding.ActivityScriptEditBinding
import com.autoclick.pro.model.Action
import com.autoclick.pro.model.ActionType
import com.autoclick.pro.model.Script
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * 脚本编辑Activity
 */
class ScriptEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SCRIPT_ID = "script_id"
    }
    
    private lateinit var binding: ActivityScriptEditBinding
    private lateinit var scriptRepository: ScriptRepository
    private lateinit var actionsAdapter: ActionsAdapter
    
    private var scriptId: String? = null
    private var script: Script? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScriptEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        scriptRepository = ScriptRepository.getInstance(this)
        scriptId = intent.getStringExtra(EXTRA_SCRIPT_ID)
        
        setupToolbar()
        setupRecyclerView()
        setupListeners()
        loadScript()
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_save -> {
                    saveScript()
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupRecyclerView() {
        actionsAdapter = ActionsAdapter(
            onEditClick = { action -> editAction(action) },
            onDeleteClick = { action -> deleteAction(action) },
            onMoveUp = { action -> moveActionUp(action) },
            onMoveDown = { action -> moveActionDown(action) }
        )
        
        binding.actionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ScriptEditActivity)
            adapter = actionsAdapter
        }
    }
    
    private fun setupListeners() {
        // 添加操作按钮
        binding.addActionFab.setOnClickListener {
            showAddActionDialog()
        }
        
        // 循环次数
        binding.loopCountSlider.addOnChangeListener { _, value, _ ->
            script?.loopCount = value.toInt()
        }
        
        // 循环间隔
        binding.loopDelaySlider.addOnChangeListener { _, value, _ ->
            script?.loopDelay = value.toLong()
        }
        
        // 执行速度
        binding.speedSlider.addOnChangeListener { _, value, _ ->
            script?.playbackSpeed = value
        }
    }
    
    private fun loadScript() {
        scriptId?.let { id ->
            script = scriptRepository.getScript(id)
            script?.let { s ->
                binding.nameEditText.setText(s.name)
                binding.descriptionEditText.setText(s.description)
                binding.loopCountSlider.value = s.loopCount.toFloat()
                binding.loopDelaySlider.value = s.loopDelay.toFloat()
                binding.speedSlider.value = s.playbackSpeed
                actionsAdapter.updateActions(s.actions)
            }
        }
        
        if (script == null) {
            script = Script(name = "新脚本")
        }
    }
    
    private fun saveScript() {
        val name = binding.nameEditText.text.toString().ifBlank { "未命名脚本" }
        val description = binding.descriptionEditText.text.toString()
        
        script?.let { s ->
            s.name = name
            s.description = description
            s.actions.clear()
            s.actions.addAll(actionsAdapter.getActions())
            
            if (scriptId != null) {
                scriptRepository.updateScript(s)
            } else {
                scriptRepository.addScript(s)
            }
            
            Toast.makeText(this, R.string.save, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun showAddActionDialog() {
        val actionTypes = arrayOf(
            getString(R.string.action_click),
            getString(R.string.action_long_click),
            getString(R.string.action_swipe),
            getString(R.string.action_wait),
            getString(R.string.action_loop)
        )
        
        MaterialAlertDialogBuilder(this)
            .setTitle("添加操作")
            .setItems(actionTypes) { _, which ->
                when (which) {
                    0 -> addAction(ActionType.CLICK)
                    1 -> addAction(ActionType.LONG_CLICK)
                    2 -> addAction(ActionType.SWIPE)
                    3 -> addAction(ActionType.WAIT)
                    4 -> addLoopAction()
                }
            }
            .show()
    }
    
    private fun addAction(type: ActionType) {
        val action = when (type) {
            ActionType.CLICK -> Action(type = type, x = 500f, y = 500f, duration = 100)
            ActionType.LONG_CLICK -> Action(type = type, x = 500f, y = 500f, duration = 500)
            ActionType.SWIPE -> Action(type = type, x = 200f, y = 500f, endX = 800f, endY = 500f, duration = 300)
            ActionType.WAIT -> Action(type = type, duration = 1000)
            else -> Action(type = type)
        }
        
        actionsAdapter.addAction(action)
    }
    
    private fun addLoopAction() {
        // 添加循环开始和结束
        actionsAdapter.addAction(Action(type = ActionType.LOOP_START, loopCount = 3))
        actionsAdapter.addAction(Action(type = ActionType.LOOP_END))
    }
    
    private fun editAction(action: Action) {
        // 显示编辑对话框
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_action, null)
        
        // 根据操作类型设置不同的编辑选项
        when (action.type) {
            ActionType.CLICK, ActionType.LONG_CLICK -> {
                dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.xInput).editText?.setText(action.x.toString())
                dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.yInput).editText?.setText(action.y.toString())
                dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.durationInput).editText?.setText(action.duration.toString())
            }
            ActionType.SWIPE -> {
                // 滑动编辑
            }
            ActionType.WAIT -> {
                dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.durationInput).editText?.setText(action.duration.toString())
            }
            ActionType.LOOP_START -> {
                dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.loopCountInput).editText?.setText(action.loopCount.toString())
            }
            else -> {}
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("编辑操作")
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                // 保存修改
                actionsAdapter.notifyDataSetChanged()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun deleteAction(action: Action) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage("确定要删除此操作吗？")
            .setPositiveButton(R.string.delete) { _, _ ->
                actionsAdapter.removeAction(action)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun moveActionUp(action: Action) {
        actionsAdapter.moveActionUp(action)
    }
    
    private fun moveActionDown(action: Action) {
        actionsAdapter.moveActionDown(action)
    }
}
