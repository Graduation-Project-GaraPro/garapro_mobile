package com.example.garapro.ui.emergency_technician

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.emergencies.Emergency

class EmergencyAdapter(private val onAccept: (Emergency) -> Unit) :
    ListAdapter<Emergency, EmergencyAdapter.ViewHolder>(EmergencyDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_emergency_order, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val emergency = getItem(position)
        holder.bind(emergency)
        holder.itemView.setOnClickListener {
            onAccept(emergency)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(emergency: Emergency) {
            itemView.findViewById<TextView>(R.id.tvEmergencyId).text = "Đơn #${emergency.id.takeLast(4)}"
            itemView.findViewById<TextView>(R.id.tvLocation).text = "Vị trí: ${emergency.latitude}, ${emergency.longitude}"
        }
    }
}


class EmergencyDiffCallback : DiffUtil.ItemCallback<Emergency>() {
    override fun areItemsTheSame(oldItem: Emergency, newItem: Emergency): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Emergency, newItem: Emergency): Boolean {
        return oldItem == newItem
    }
}