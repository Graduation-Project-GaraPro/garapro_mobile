package com.example.garapro.ui.repairRequest

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.repairRequest.Service
import com.example.garapro.utils.MoneyUtils
import com.google.android.material.card.MaterialCardView
import android.util.Log
class ServiceSimpleAdapter(
    private var services: List<Service>,
    private val onServiceSelected: (Service) -> Unit,
    private val isServiceSelected: (Service) -> Boolean = { false }
) : RecyclerView.Adapter<ServiceSimpleAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvServiceName: TextView = itemView.findViewById(R.id.tvServiceName)
        val tvServicePrice: TextView = itemView.findViewById(R.id.tvServicePrice)
        val tvServiceDescription: TextView = itemView.findViewById(R.id.tvServiceDescription)
        val cardService: MaterialCardView = itemView.findViewById(R.id.cardService)
        val checkboxService: CheckBox = itemView.findViewById(R.id.checkboxService)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_service_simple, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val service = services[position]
        val isSelected = isServiceSelected(service)

        Log.d("ServiceAdapter", "Binding service: ${service.serviceName}, isSelected: $isSelected")

        // Cập nhật UI ban đầu
        updateServiceUI(holder, service, isSelected)

        // QUAN TRỌNG: Remove listener cũ trước khi set mới
        holder.cardService.setOnClickListener(null)

        // Set click listener cho CardView
        holder.cardService.setOnClickListener {
            Log.d("ServiceAdapter", "Card clicked for: ${service.serviceName}")

            // Gọi callback để toggle selection trong ViewModel
            onServiceSelected(service)

            // Lấy trạng thái MỚI sau khi toggle
            val newIsSelected = isServiceSelected(service)
            Log.d("ServiceAdapter", "After toggle - newIsSelected: $newIsSelected")

            // Update UI ngay lập tức
            holder.checkboxService.isChecked = newIsSelected
            updateCardStroke(holder, newIsSelected)
        }

        // Checkbox chỉ để hiển thị, không cho click
        holder.checkboxService.isClickable = false
        holder.checkboxService.isFocusable = false
        holder.checkboxService.setOnClickListener(null)
    }

    private fun updateServiceUI(holder: ViewHolder, service: Service, isSelected: Boolean) {
        // Update checkbox
        holder.checkboxService.isChecked = isSelected

        // Update text
        holder.tvServiceName.text = service.serviceName

        // Format giá
        val servicePrice = MoneyUtils.calculateServicePrice(service)
        if (service.discountedPrice > 0 && service.discountedPrice < service.price) {
            holder.tvServicePrice.text = "${MoneyUtils.formatVietnameseCurrency(servicePrice)} (Giảm ${MoneyUtils.formatVietnameseCurrency(service.price - service.discountedPrice)})"
            holder.tvServicePrice.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.success))
        } else {
            holder.tvServicePrice.text = MoneyUtils.formatVietnameseCurrency(servicePrice)
            holder.tvServicePrice.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.primary))
        }

        holder.tvServiceDescription.text = service.description

        // Update card stroke
        updateCardStroke(holder, isSelected)
    }

    private fun updateCardStroke(holder: ViewHolder, isSelected: Boolean) {
        val strokeColor = if (isSelected) {
            ContextCompat.getColor(holder.itemView.context, R.color.primary)
        } else {
            ContextCompat.getColor(holder.itemView.context, R.color.divider)
        }

        holder.cardService.strokeColor = strokeColor
        holder.cardService.strokeWidth = if (isSelected) 2 else 1
    }

    override fun getItemCount() = services.size

    fun updateData(newServices: List<Service>) {
        services = newServices
        notifyDataSetChanged()
    }
}