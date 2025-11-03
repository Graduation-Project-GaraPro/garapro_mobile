package com.example.garapro.ui.repairRequest

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.repairRequest.Part
import com.example.garapro.data.model.repairRequest.Service
import com.example.garapro.utils.MoneyUtils

class ServicePartsAdapter(
    private var services: List<Service>,
    private val onPartSelected: (Service, Part) -> Unit,
    private val isPartSelected: (Part) -> Boolean
) : RecyclerView.Adapter<ServicePartsAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvServiceName: TextView = itemView.findViewById(R.id.tvServiceName)
        val partsContainer: LinearLayout = itemView.findViewById(R.id.partsContainer)
//        val tvSelectedParts: TextView = itemView.findViewById(R.id.tvSelectedParts)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_service_booking_parts, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val service = services[position]

        holder.tvServiceName.text = service.serviceName

        // Hiển thị parts đã chọn cho service này
        updateSelectedPartsDisplay(holder, service)

        setupPartsUI(holder, service)
    }

    private fun updateSelectedPartsDisplay(holder: ViewHolder, service: Service) {
        // Lấy tất cả parts đã chọn thuộc service này
        val selectedPartsForService = getSelectedPartsForService(service)
        if (selectedPartsForService.isNotEmpty()) {
//            holder.tvSelectedParts.visibility = View.VISIBLE
//            holder.tvSelectedParts.text = "Đã chọn: ${selectedPartsForService.joinToString(", ") { it.name }}"
        } else {
//            holder.tvSelectedParts.visibility = View.GONE
        }
    }

    private fun getSelectedPartsForService(service: Service): List<Part> {
        // Lấy tất cả parts đã chọn thuộc service này
        return service.partCategories.flatMap { category ->
            category.parts.filter { part -> isPartSelected(part) }
        }
    }

    private fun setupPartsUI(holder: ViewHolder, service: Service) {
        holder.partsContainer.removeAllViews()

        if (service.partCategories.isEmpty()) {
            val tvNoParts = TextView(holder.itemView.context).apply {
                text = "Không có linh kiện"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                setPadding(0, 8, 0, 8)
            }
            holder.partsContainer.addView(tvNoParts)
            return
        }

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

        service.partCategories.forEach { partCategory ->
            val tvCategory = TextView(holder.itemView.context).apply {
                text = "${partCategory.categoryName}:"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setPadding(0, 8, 0, 4)
            }
            holder.partsContainer.addView(tvCategory)

            // Lấy part đã chọn trong partCategory này (nếu có)
            val selectedPartInCategory = partCategory.parts.find { isPartSelected(it) }

            partCategory.parts.forEach { part ->
                val partView = LayoutInflater.from(holder.itemView.context)
                    .inflate(R.layout.item_booking_part_selectable, holder.partsContainer, false)

                val tvPartName = partView.findViewById<TextView>(R.id.tvPartName)
                val tvPartPrice = partView.findViewById<TextView>(R.id.tvPartPrice)
                val checkboxPart = partView.findViewById<CheckBox>(R.id.checkboxPart)
                val tvStock = partView.findViewById<TextView>(R.id.tvStock)

                tvPartName.text = part.name
                tvPartPrice.text = MoneyUtils.formatVietnameseCurrency(part.price)
                tvStock.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.success))

                // Logic enable/disable dựa trên service type
                if (!service.isAdvanced) {
                    // Service không advanced: nếu đã có part được chọn trong partCategory này
                    // thì chỉ enable part đó, disable các part khác
//                    checkboxPart.isEnabled = selectedPartInCategory == null || part.partId == selectedPartInCategory.partId
                } else {
                    // Service advanced: luôn enable tất cả parts
//                    checkboxPart.isEnabled = true
                }

                checkboxPart.isChecked = isPartSelected(part)

                partView.setOnClickListener {
                    if (checkboxPart.isEnabled) {
                        // Toggle trạng thái check
                        val newCheckedState = !checkboxPart.isChecked
                        checkboxPart.isChecked = newCheckedState
                        // Gọi callback chỉ khi enabled
                        onPartSelected(service, part)
                    }
                }

                // QUAN TRỌNG: Sửa checked change listener


                checkboxPart.setOnCheckedChangeListener { _, isChecked ->
                    // Chỉ xử lý khi user tương tác trực tiếp với checkbox
                    if (checkboxPart.isPressed && checkboxPart.isEnabled) {
                        onPartSelected(service, part)
                    }
                }

                // QUAN TRỌNG: Ngăn event propagation từ checkbox
                checkboxPart.setOnClickListener {
                    it.isPressed = true // Đánh dấu là user pressed
                    // Không cần làm gì thêm vì checked change listener sẽ xử lý
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