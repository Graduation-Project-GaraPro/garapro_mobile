package com.example.garapro.ui.repairRequest

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.repairRequest.Vehicle
import com.google.android.material.card.MaterialCardView

class VehicleAdapter(
    private var vehicles: List<Vehicle>,
    private val onVehicleSelected: (Vehicle) -> Unit
) : RecyclerView.Adapter<VehicleAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvVehicleInfo: TextView = itemView.findViewById(R.id.tvVehicleInfo)
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardVehicle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_booking_vehicle, parent, false)
        return ViewHolder(view)
    }
    private var selectedPosition = RecyclerView.NO_POSITION
    fun getPositionOf(vehicle: Vehicle): Int {
        return vehicles.indexOfFirst { it.vehicleID == vehicle.vehicleID }
    }

    fun setSelectedPosition(position: Int) {
        val previousPosition = selectedPosition
        selectedPosition = position
        if (previousPosition != RecyclerView.NO_POSITION) notifyItemChanged(previousPosition)
        if (selectedPosition != RecyclerView.NO_POSITION) notifyItemChanged(selectedPosition)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val vehicle = vehicles[position]
        holder.tvVehicleInfo.text = "${vehicle.brandName} ${vehicle.modelName} - ${vehicle.licensePlate}"

        // Highlight selected item
        val colorRes = if (position == selectedPosition) R.color.primary_color else R.color.white
        holder.cardView.setCardBackgroundColor(
            ContextCompat.getColor(holder.itemView.context, colorRes)
        )

        holder.cardView.setOnClickListener {
            val currentPosition = holder.adapterPosition
            if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener

            val previousPosition = selectedPosition
            selectedPosition = currentPosition

            if (previousPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(previousPosition)
            }
            notifyItemChanged(selectedPosition)

            onVehicleSelected(vehicles[selectedPosition])
        }
    }

    fun updateData(newVehicles: List<Vehicle>) {
        vehicles = newVehicles
        notifyDataSetChanged() // thông báo RecyclerView cập nhật
    }

    override fun getItemCount() = vehicles.size
}