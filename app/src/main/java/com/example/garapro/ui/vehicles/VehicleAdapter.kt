package com.example.garapro.ui.vehicles

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R

class VehicleAdapter(
    private val vehicles: List<Vehicle>,
    private val onItemClick: (Vehicle) -> Unit,
    private val onEditClick: (Vehicle) -> Unit,
    private val onDeleteClick: (Vehicle) -> Unit
) : RecyclerView.Adapter<VehicleAdapter.VehicleViewHolder>() {

    class VehicleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvLicensePlate: TextView = view.findViewById(R.id.tvLicensePlate)
        val tvVehicleInfo: TextView = view.findViewById(R.id.tvVehicleInfo)
        val tvYear: TextView = view.findViewById(R.id.tvYear)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VehicleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vehicle, parent, false)
        return VehicleViewHolder(view)
    }

    override fun onBindViewHolder(holder: VehicleViewHolder, position: Int) {
        val vehicle = vehicles[position]

        holder.tvLicensePlate.text = vehicle.licensePlate
        holder.tvVehicleInfo.text = "${vehicle.brand} ${vehicle.model} - ${vehicle.color}"
        holder.tvYear.text = vehicle.year.toString()

        holder.itemView.setOnClickListener { onItemClick(vehicle) }
        holder.btnEdit.setOnClickListener { onEditClick(vehicle) }
        holder.btnDelete.setOnClickListener { onDeleteClick(vehicle) }
    }

    override fun getItemCount() = vehicles.size
}