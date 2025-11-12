package com.example.garapro.ui.repairRequest

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.repairRequest.Part
import com.example.garapro.data.model.repairRequest.Service
import com.example.garapro.utils.MoneyUtils
import com.google.android.material.card.MaterialCardView

class ServiceItemAdapter(
    private var services: List<Service>,
    private val onServiceSelected: (Service) -> Unit,
    private val onPartSelected: (Service, Part) -> Unit,
    private val isServiceSelected: (Service) -> Boolean,
    private val isPartSelected: (Part) -> Boolean
) : RecyclerView.Adapter<ServiceItemAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvServiceName: TextView = itemView.findViewById(R.id.tvServiceName)
        val tvServicePrice: TextView = itemView.findViewById(R.id.tvServicePrice)
        val tvServiceDescription: TextView = itemView.findViewById(R.id.tvServiceDescription)
        val cardService: LinearLayout = itemView.findViewById(R.id.cardService) // Đổi từ MaterialCardView sang LinearLayout
        val partsContainer: LinearLayout = itemView.findViewById(R.id.partsContainer)
        val checkboxService: CheckBox = itemView.findViewById(R.id.checkboxService)
        val badgeAdvanced: TextView = itemView.findViewById(R.id.badgeAdvanced)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_booking_service, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val service = services[position]

        val isSelected = isServiceSelected(service)
        holder.checkboxService.isChecked = isSelected

        // Hiển thị badge advanced đơn giản
        holder.badgeAdvanced.visibility = if (service.isAdvanced) View.VISIBLE else View.GONE

        holder.tvServiceName.text = service.serviceName

        // Format giá đơn giản
        val servicePrice = MoneyUtils.calculateServicePrice(service)
        if (service.discountedPrice > 0 && service.discountedPrice < service.price) {
            holder.tvServicePrice.text = "${MoneyUtils.formatVietnameseCurrency(servicePrice)} (Giảm ${MoneyUtils.formatVietnameseCurrency(service.price - service.discountedPrice)})"
            holder.tvServicePrice.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.success))
        } else {
            holder.tvServicePrice.text = MoneyUtils.formatVietnameseCurrency(servicePrice)
            holder.tvServicePrice.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.primary))
        }

        holder.tvServiceDescription.text = service.description

        setupPartsUI(holder, service, isSelected)

        // Thêm background khi selected
        holder.cardService.setBackgroundColor(
            ContextCompat.getColor(holder.itemView.context,
                if (isSelected) R.color.background else R.color.surface
            )
        )

        holder.cardService.setOnClickListener {
            onServiceSelected(service)
        }

        holder.checkboxService.setOnCheckedChangeListener { _, isChecked ->
            if (holder.checkboxService.isPressed) {
                onServiceSelected(service)
            }
        }
    }

    private fun setupPartsUI(holder: ViewHolder, service: Service, isServiceSelected: Boolean) {
        holder.partsContainer.removeAllViews()

        if (!isServiceSelected) return

        if (service.partCategories.isNotEmpty()) {
            val instructionText = if (service.isAdvanced) {
                "Linh kiện (Có thể chọn nhiều linh kiện từ các nhóm khác nhau):"
            } else {
                "Linh kiện (Chỉ được chọn 1 linh kiện):"
            }

            val tvPartsTitle = TextView(holder.itemView.context).apply {
                text = instructionText
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                setPadding(0, 8, 0, 8)
            }
            holder.partsContainer.addView(tvPartsTitle)
        }

        service.partCategories.forEach { category ->
            val tvCategory = TextView(holder.itemView.context).apply {
                text = "${category.categoryName}:"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setPadding(0, 8, 0, 4)
            }
            holder.partsContainer.addView(tvCategory)

            category.parts.forEach { part ->
                val partView = LayoutInflater.from(holder.itemView.context)
                    .inflate(R.layout.item_booking_part, holder.partsContainer, false)

                val tvPartName = partView.findViewById<TextView>(R.id.tvPartName)
                val tvPartPrice = partView.findViewById<TextView>(R.id.tvPartPrice)
                val checkboxPart = partView.findViewById<CheckBox>(R.id.checkboxPart)
                val tvStock = partView.findViewById<TextView>(R.id.tvStock)

                tvPartName.text = part.name
                tvPartPrice.text = MoneyUtils.formatVietnameseCurrency(part.price)

                // Hiển thị stock đơn giản
//                tvStock.text = if (part.stock > 0) "Còn hàng" else "Hết hàng"
                tvStock.text = ""
                tvStock.setTextColor(ContextCompat.getColor(holder.itemView.context,
                    if (part.stock > 0) R.color.success else R.color.text_secondary
                ))

//                checkboxPart.isEnabled = isServiceSelected && part.stock > 0
                checkboxPart.isEnabled = isServiceSelected
                checkboxPart.isChecked = isPartSelected(part)

                partView.setOnClickListener {
//                    if (isServiceSelected && part.stock > 0) {
//                        onPartSelected(service, part)
//                    } else if (part.stock <= 0) {
//                        Toast.makeText(holder.itemView.context, "Linh kiện đã hết hàng", Toast.LENGTH_SHORT).show()
//                    }
                    if (isServiceSelected ) {
                        onPartSelected(service, part)
                    }
                }

                checkboxPart.setOnCheckedChangeListener { _, isChecked ->
//                    if (checkboxPart.isPressed && isServiceSelected && part.stock > 0) {
//                        onPartSelected(service, part)
//                    }
                    if (checkboxPart.isPressed && isServiceSelected ) {
                        onPartSelected(service, part)
                    }
                }

                holder.partsContainer.addView(partView)
            }
        }
    }

    override fun getItemCount() = services.size

    fun updateData(newServices: List<Service>) {
        services = newServices
        notifyDataSetChanged()
    }
}