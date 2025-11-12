package com.example.garapro.ui.repairRequest

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.repairRequest.Part
import com.example.garapro.data.model.repairRequest.Service
import com.example.garapro.data.model.repairRequest.ServiceCategory
import com.example.garapro.data.model.repairRequest.Vehicle

class ServiceAdapter(
    public var serviceCategories: List<ServiceCategory>,
    private val onServiceSelected: (Service) -> Unit,
    private val onPartSelected: (Service, Part) -> Unit,
    private val isServiceSelected: (Service) -> Boolean,
    private val isPartSelected: (Part) -> Boolean
) : RecyclerView.Adapter<ServiceAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCategoryName: TextView = itemView.findViewById(R.id.tvCategoryName)
        val rvServices: RecyclerView = itemView.findViewById(R.id.rvServices)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_booking_service_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = serviceCategories[position]
        holder.tvCategoryName.text = category.categoryName

        val serviceAdapter = ServiceItemAdapter(
            category.services,
            onServiceSelected,
            onPartSelected,
            isServiceSelected,
            isPartSelected
        )
        holder.rvServices.adapter = serviceAdapter
        holder.rvServices.layoutManager = LinearLayoutManager(holder.itemView.context)

        // Fix chiều cao cho RecyclerView con
        holder.rvServices.isNestedScrollingEnabled = false
    }

    fun updateData(serviceCate: List<ServiceCategory>) {
        serviceCategories = serviceCate
        notifyDataSetChanged()
    }

    override fun getItemCount() = serviceCategories.size
}