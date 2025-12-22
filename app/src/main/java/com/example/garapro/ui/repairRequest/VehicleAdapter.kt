package com.example.garapro.ui.repairRequest

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.Vehicles.VehicleSelectableDto
import com.google.android.material.card.MaterialCardView

class VehicleAdapter(
    private var vehicles: List<VehicleSelectableDto>,
    private val onVehicleSelected: (VehicleSelectableDto) -> Unit
) : RecyclerView.Adapter<VehicleAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvVehicleInfo: TextView = itemView.findViewById(R.id.tvVehicleInfo)
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardVehicle)

        // OPTIONAL: nếu layout có thêm TextView để show trạng thái
        val tvVehicleStatus: TextView? = itemView.findViewById<TextView?>(R.id.tvVehicleStatus)
    }

    private var selectedPosition = RecyclerView.NO_POSITION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_booking_vehicle, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = vehicles.size

    fun getPositionOf(vehicle: VehicleSelectableDto): Int {
        return vehicles.indexOfFirst { it.vehicleId == vehicle.vehicleId }
    }

    fun setSelectedPosition(position: Int) {
        val previousPosition = selectedPosition
        selectedPosition = position
        if (previousPosition != RecyclerView.NO_POSITION) notifyItemChanged(previousPosition)
        if (selectedPosition != RecyclerView.NO_POSITION) notifyItemChanged(selectedPosition)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val vehicle = vehicles[position]

        // --- text hiển thị ---
        holder.tvVehicleInfo.text =
            "${vehicle.brandName ?: ""} ${vehicle.modelName ?: ""} - ${vehicle.licensePlate ?: ""}"

        // --- disable item nếu không selectable ---
        val enabled = vehicle.isSelectable

        holder.cardView.isEnabled = enabled
        holder.itemView.isEnabled = enabled

        // làm mờ nếu disabled
        val alpha = if (enabled) 1.0f else 0.8f
        holder.cardView.alpha = alpha
        holder.tvVehicleInfo.alpha = alpha

        // --- status text (nếu layout có tvVehicleStatus) ---
        holder.tvVehicleStatus?.let { tv ->
            val statusText = buildStatusText(vehicle)
            if (statusText.isNullOrBlank()) {
                tv.visibility = View.GONE
            } else {
                tv.visibility = View.VISIBLE
                tv.text = statusText
                tv.alpha = alpha
            }
        }

        // --- highlight selected ---
        val isSelected = position == selectedPosition
        val bgColorRes = if (isSelected) R.color.primary_color else R.color.white
        holder.cardView.setCardBackgroundColor(
            ContextCompat.getColor(holder.itemView.context, bgColorRes)
        )

        // --- click ---
        holder.cardView.setOnClickListener {
            val currentPosition = holder.adapterPosition
            if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener

            val clicked = vehicles[currentPosition]
            if (!clicked.isSelectable) return@setOnClickListener // chặn click xe không chọn được

            val previousPosition = selectedPosition
            selectedPosition = currentPosition

            if (previousPosition != RecyclerView.NO_POSITION) notifyItemChanged(previousPosition)
            notifyItemChanged(selectedPosition)

            onVehicleSelected(clicked)
        }
    }

    fun updateData(newVehicles: List<VehicleSelectableDto>) {
        vehicles = newVehicles
        // reset selection nếu xe cũ không còn
        if (selectedPosition != RecyclerView.NO_POSITION) {
            val selectedId = vehicles.getOrNull(selectedPosition)?.vehicleId
            if (selectedId == null) selectedPosition = RecyclerView.NO_POSITION
        }
        notifyDataSetChanged()
    }

    private fun buildStatusText(vehicle: VehicleSelectableDto): String? {
        // Ưu tiên flags trước cho chắc, vì state có thể thay đổi enum
        return when {
            vehicle.hasActiveRepairRequest -> "Repair request in progress"
            vehicle.hasOpenRepairOrder -> "Vehicle is currently in the workshop"
            else -> null
        }
    }
}
