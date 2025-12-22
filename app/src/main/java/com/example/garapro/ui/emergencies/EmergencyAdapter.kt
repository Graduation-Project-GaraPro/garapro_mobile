package com.example.garapro.ui.emergencies

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.ui.emergencies.EmergencySummary

class EmergencyAdapter(
    private val onTrackClick: (EmergencySummary) -> Unit,
    private val onDetailClick: (EmergencySummary) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<EmergencySummary> = emptyList()
    private var isCurrentTab: Boolean = true

    fun submitList(list: List<EmergencySummary>, isCurrent: Boolean) {
        this.items = list
        this.isCurrentTab = isCurrent
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_CURRENT) {
            val view = inflater.inflate(R.layout.item_emergency_current, parent, false)
            CurrentViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_emergency_past, parent, false)
            PastViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is CurrentViewHolder) {
            holder.bind(item)
        } else if (holder is PastViewHolder) {
            holder.bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return if (isCurrentTab) VIEW_TYPE_CURRENT else VIEW_TYPE_PAST
    }

    inner class CurrentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvTechName: TextView = itemView.findViewById(R.id.tvTechName)
        private val tvEta: TextView = itemView.findViewById(R.id.tvEta)
        private val btnTrack: Button = itemView.findViewById(R.id.btnTrackDriver)

        fun bind(item: EmergencySummary) {
            val statusText = item.status.replace("_", " ").uppercase()
            tvStatus.text = statusText
            
            // Color logic based on status string
            val statusLower = item.status.lowercase()
            
            tvTechName.text = "Tech: ${item.technicianName ?: "Waiting..."}"
            tvEta.text = "ETA: -- mins" // Could calculate if we have data

            btnTrack.setOnClickListener { onTrackClick(item) }
            
            // Hide Track button if not relevant
            if (statusLower == "pending" || statusLower == "accepted") {
                btnTrack.visibility = View.GONE
            } else {
                btnTrack.visibility = View.VISIBLE
            }
        }
    }

    inner class PastViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatusPast)
        private val containerStatus: View = itemView.findViewById(R.id.containerStatus)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvPlate: TextView = itemView.findViewById(R.id.tvPlate)
        private val tvAddress: TextView = itemView.findViewById(R.id.tvAddress)
        private val btnDetails: Button = itemView.findViewById(R.id.btnViewDetails)

        fun bind(item: EmergencySummary) {
            val statusText = item.status.replace("_", " ")
            tvStatus.text = statusText.lowercase().replaceFirstChar { it.uppercase() }
            
            tvDate.text = item.time
            tvPlate.text = item.vehicleTitle
            tvAddress.text = item.garageName

            // Style based on status
            val statusLower = item.status.lowercase()
            if (statusLower == "cancelled" || statusLower == "canceled" || statusLower == "expired") {
                tvStatus.setTextColor(android.graphics.Color.RED)
                (itemView.findViewById<android.widget.ImageView>(R.id.imgStatusIcon)).setImageResource(R.drawable.ic_close)
                containerStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFEBEE"))
            } else {
                tvStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                (itemView.findViewById<android.widget.ImageView>(R.id.imgStatusIcon)).setImageResource(R.drawable.ic_check_circle_green)
                containerStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E8F5E9"))
            }

            btnDetails.setOnClickListener { onDetailClick(item) }
        }
    }

    companion object {
        private const val VIEW_TYPE_CURRENT = 0
        private const val VIEW_TYPE_PAST = 1
    }
}