package com.adbutility.guard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.adbutility.guard.data.DetectionEvent
import com.adbutility.guard.databinding.ItemHistoryBinding

class HistoryAdapter(
    private var events: List<DetectionEvent> = emptyList()
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = events[position]
        holder.binding.tvItemType.text = item.detectionType
        holder.binding.tvItemPackage.text = holder.itemView.context.getString(R.string.label_package) + " " + item.packageName
        holder.binding.tvItemAction.text = item.actionTaken
        holder.binding.tvItemTime.text = item.getFormattedDate()
    }

    override fun getItemCount(): Int = events.size

    fun updateData(newEvents: List<DetectionEvent>) {
        this.events = newEvents
        notifyDataSetChanged()
    }
}

