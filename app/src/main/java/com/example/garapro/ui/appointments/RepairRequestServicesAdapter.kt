package com.example.garapro.ui.appointments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.repairRequest.PartDetail
import com.example.garapro.data.model.repairRequest.RequestServiceDetail
import com.example.garapro.utils.MoneyUtils

class RepairRequestServicesAdapter(private val services: List<RequestServiceDetail>) :
    RecyclerView.Adapter<RepairRequestServicesAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(service: RequestServiceDetail) {
            itemView.findViewById<TextView>(R.id.tvServiceName).text = service.serviceName
            itemView.findViewById<TextView>(R.id.tvServicePrice).text = "${MoneyUtils.formatVietnameseCurrency(service.price)}"

            // Setup parts
//            val rvParts = itemView.findViewById<RecyclerView>(R.id.rvParts)
//            val adapter = PartsAdapter(service.parts)
//            rvParts.adapter = adapter
//            rvParts.layoutManager = LinearLayoutManager(itemView.context)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_service_with_parts_detail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(services[position])
    }

    override fun getItemCount(): Int = services.size
}

class PartsAdapter(private val parts: List<PartDetail>) : RecyclerView.Adapter<PartsAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(part: PartDetail) {
            itemView.findViewById<TextView>(R.id.tvPartName).text = part.partName
//            itemView.findViewById<TextView>(R.id.tvPartQuantity).text = "Qty: ${part.}"
            itemView.findViewById<TextView>(R.id.tvPartPrice).text = "${MoneyUtils.formatVietnameseCurrency(part.price)}"

            // Ẩn quantity nếu = 0
//            val tvQuantity = itemView.findViewById<TextView>(R.id.tvPartQuantity)
//            tvQuantity.visibility = if (part.quantity > 0) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_part_detail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(parts[position])
    }

    override fun getItemCount(): Int = parts.size
}