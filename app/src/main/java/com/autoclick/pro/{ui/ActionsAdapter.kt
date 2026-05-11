package com.autoclick.pro.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.autoclick.pro.R
import com.autoclick.pro.databinding.ItemActionBinding
import com.autoclick.pro.model.Action
import com.autoclick.pro.model.ActionType

/**
 * 操作列表适配器
 */
class ActionsAdapter(
    private val onEditClick: (Action) -> Unit,
    private val onDeleteClick: (Action) -> Unit,
    private val onMoveUp: (Action) -> Unit,
    private val onMoveDown: (Action) -> Unit
) : RecyclerView.Adapter<ActionsAdapter.ActionViewHolder>() {
    
    private val actions = mutableListOf<Action>()
    
    @SuppressLint("NotifyDataSetChanged")
    fun updateActions(newActions: List<Action>) {
        actions.clear()
        actions.addAll(newActions)
        notifyDataSetChanged()
    }
    
    fun getActions(): List<Action> = actions.toList()
    
    @SuppressLint("NotifyDataSetChanged")
    fun addAction(action: Action) {
        actions.add(action)
        notifyItemInserted(actions.size - 1)
    }
    
    @SuppressLint("NotifyDataSetChanged")
    fun removeAction(action: Action) {
        val index = actions.indexOf(action)
        if (index != -1) {
            actions.removeAt(index)
            notifyItemRemoved(index)
        }
    }
    
    fun moveActionUp(action: Action) {
        val index = actions.indexOf(action)
        if (index > 0) {
            actions.removeAt(index)
            actions.add(index - 1, action)
            notifyItemMoved(index, index - 1)
        }
    }
    
    fun moveActionDown(action: Action) {
        val index = actions.indexOf(action)
        if (index < actions.size - 1) {
            actions.removeAt(index)
            actions.add(index + 1, action)
            notifyItemMoved(index, index + 1)
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionViewHolder {
        val binding = ItemActionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ActionViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ActionViewHolder, position: Int) {
        holder.bind(actions[position], position)
    }
    
    override fun getItemCount(): Int = actions.size
    
    inner class ActionViewHolder(
        private val binding: ItemActionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.moreButton.setOnClickListener { view ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    showPopupMenu(view, actions[position])
                }
            }
        }
        
        fun bind(action: Action, position: Int) {
            binding.actionNumber.text = "${position + 1}"
            
            val context = binding.root.context
            binding.actionType.text = when (action.type) {
                ActionType.CLICK -> context.getString(R.string.action_click)
                ActionType.LONG_CLICK -> context.getString(R.string.action_long_click)
                ActionType.SWIPE -> context.getString(R.string.action_swipe)
                ActionType.WAIT -> context.getString(R.string.action_wait)
                ActionType.LOOP_START -> "循环开始 (${action.loopCount}次)"
                ActionType.LOOP_END -> "循环结束"
                ActionType.CONDITION -> context.getString(R.string.action_condition)
                ActionType.GESTURE -> "手势"
            }
            
            binding.actionDetails.text = when (action.type) {
                ActionType.CLICK, ActionType.LONG_CLICK -> 
                    "位置: (${action.x.toInt()}, ${action.y.toInt()}), 持续: ${action.duration}ms"
                ActionType.SWIPE -> 
                    "从 (${action.x.toInt()}, ${action.y.toInt()}) 到 (${action.endX.toInt()}, ${action.endY.toInt()})"
                ActionType.WAIT -> 
                    "等待: ${action.duration}ms"
                else -> ""
            }
            
            // 循环操作特殊样式
            if (action.type == ActionType.LOOP_START || action.type == ActionType.LOOP_END) {
                binding.actionType.setTextColor(context.getColor(R.color.accent))
            } else {
                binding.actionType.setTextColor(context.getColor(R.color.text_primary))
            }
        }
        
        private fun showPopupMenu(view: View, action: Action) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.action_menu, popup.menu)
            
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit -> {
                        onEditClick(action)
                        true
                    }
                    R.id.action_delete -> {
                        onDeleteClick(action)
                        true
                    }
                    R.id.action_move_up -> {
                        onMoveUp(action)
                        true
                    }
                    R.id.action_move_down -> {
                        onMoveDown(action)
                        true
                    }
                    else -> false
                }
            }
            
            popup.show()
        }
    }
}
