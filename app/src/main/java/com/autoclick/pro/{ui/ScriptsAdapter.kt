package com.autoclick.pro.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.autoclick.pro.R
import com.autoclick.pro.databinding.ItemScriptBinding
import com.autoclick.pro.model.Script
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 脚本列表适配器
 */
class ScriptsAdapter(
    private val onPlayClick: (Script) -> Unit,
    private val onFloatingClick: (Script) -> Unit,
    private val onEditClick: (Script) -> Unit,
    private val onDeleteClick: (Script) -> Unit,
    private val onDuplicateClick: (Script) -> Unit
) : RecyclerView.Adapter<ScriptsAdapter.ScriptViewHolder>() {
    
    private var scripts: List<Script> = emptyList()
    private var filteredScripts: List<Script> = emptyList()
    private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    
    @SuppressLint("NotifyDataSetChanged")
    fun updateScripts(newScripts: List<Script>) {
        scripts = newScripts
        filteredScripts = newScripts
        notifyDataSetChanged()
    }
    
    @SuppressLint("NotifyDataSetChanged")
    fun filter(query: String) {
        filteredScripts = if (query.isBlank()) {
            scripts
        } else {
            scripts.filter { 
                it.name.contains(query, ignoreCase = true) || 
                it.description.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScriptViewHolder {
        val binding = ItemScriptBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ScriptViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ScriptViewHolder, position: Int) {
        holder.bind(filteredScripts[position])
    }
    
    override fun getItemCount(): Int = filteredScripts.size
    
    inner class ScriptViewHolder(
        private val binding: ItemScriptBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.playButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPlayClick(filteredScripts[position])
                }
            }
            
            binding.floatingButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onFloatingClick(filteredScripts[position])
                }
            }
            
            binding.moreButton.setOnClickListener { view ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    showPopupMenu(view, filteredScripts[position])
                }
            }
        }
        
        fun bind(script: Script) {
            binding.scriptName.text = script.name
            binding.scriptDescription.text = script.description.ifBlank { 
                binding.root.context.getString(R.string.script_description) 
            }
            binding.actionCount.text = "${script.actions.size} 个操作"
            binding.updateTime.text = dateFormat.format(Date(script.updatedAt))
            
            // 设置描述可见性
            binding.scriptDescription.visibility = if (script.description.isBlank()) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }
        
        private fun showPopupMenu(view: View, script: Script) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.script_menu, popup.menu)
            
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit -> {
                        onEditClick(script)
                        true
                    }
                    R.id.action_duplicate -> {
                        onDuplicateClick(script)
                        true
                    }
                    R.id.action_delete -> {
                        onDeleteClick(script)
                        true
                    }
                    else -> false
                }
            }
            
            popup.show()
        }
    }
}
