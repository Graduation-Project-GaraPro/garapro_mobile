package com.example.garapro.ui.appointments.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.ui.appointments.model.Appointment

class AppointmentAdapter(
    private val items: List<Appointment>,
    private val onItemClick: (Appointment) -> Unit
) : RecyclerView.Adapter<AppointmentAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtGarageName: TextView = view.findViewById(R.id.txtGarageName)
        val txtServiceName: TextView = view.findViewById(R.id.txtServiceName)
        val txtDateTime: TextView = view.findViewById(R.id.txtDateTime)
        val txtCarPlate: TextView = view.findViewById(R.id.txtCarPlate)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_appointment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.txtGarageName.text = item.garageName
        holder.txtServiceName.text = "Dịch vụ: ${item.serviceName}"
        holder.txtDateTime.text = "Thời gian: ${item.dateTime}"
        holder.txtCarPlate.text = "Xe: ${item.carPlate}"
        holder.txtStatus.text = item.status
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size
}
