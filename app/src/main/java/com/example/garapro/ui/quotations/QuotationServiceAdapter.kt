package com.example.garapro.ui.quotations

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.quotations.PartCategory
import com.example.garapro.data.model.quotations.QuotationServiceDetail
import com.example.garapro.databinding.ItemQuotationServiceBinding
import java.text.NumberFormat
import java.util.Locale

class QuotationServiceAdapter(
    private var services: List<QuotationServiceDetail>,
    private var onCheckChanged: (String, Boolean) -> Unit,
    private var onPartToggle: (String, String, String) -> Unit,
    private var isEditable: Boolean = true
) : RecyclerView.Adapter<QuotationServiceAdapter.ViewHolder>() {

    private val expandedStates = mutableMapOf<String, Boolean>()

    fun updateEditable(editable: Boolean) {
        this.isEditable = editable
        notifyDataSetChanged()
    }

    fun updateOnCheckChanged(newOnCheckChanged: (String, Boolean) -> Unit) {
        this.onCheckChanged = newOnCheckChanged
        notifyDataSetChanged()
    }

    fun updateServices(newServices: List<QuotationServiceDetail>) {
        services = newServices
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemQuotationServiceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(services[position])
    }

    override fun getItemCount() = services.size

    inner class ViewHolder(private val binding: ItemQuotationServiceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(service: QuotationServiceDetail) {
            binding.tvServiceName.text = service.serviceName
            binding.tvServiceDescription.text = service.serviceDescription
            binding.tvServicePrice.text = formatCurrency(service.totalPrice)

            // Hiển thị chip "Bắt buộc"
            binding.tvRequired.visibility = if (service.isRequired) View.VISIBLE else View.GONE

            // Vô hiệu hóa checkbox khi không được chỉnh sửa HOẶC service là required
            val canToggleService = if (service.isRequired) {
                // Service required: chỉ cho phép toggle nếu chưa được chọn
                isEditable && !service.isSelected
            } else {
                // Service không required: cho phép toggle bình thường
                isEditable && !service.isRequired
            }
            binding.cbService.isEnabled = canToggleService

            binding.cbService.setOnCheckedChangeListener(null)
            binding.cbService.isChecked = service.isSelected

            if (isEditable && canToggleService) {
                binding.cbService.setOnCheckedChangeListener { _, isChecked ->
                    onCheckChanged(service.quotationServiceId, isChecked)
                }
            }
            if (!service.isSelected) {
                // Reset expanded state
                expandedStates.remove(service.quotationServiceId)

                // Ẩn tất cả
                binding.rvPartCategories.visibility = View.GONE
                binding.btnToggleParts.visibility = View.GONE
                binding.selectedPartsSummary.visibility = View.GONE
            } else {
                // Service được chọn -> hiện toggle button
                binding.btnToggleParts.visibility = View.VISIBLE

                val isExpanded = expandedStates[service.quotationServiceId] ?: false
                setupPartCategoriesVisibility(service, isExpanded)
            }

            // Xử lý ẩn/hiện part categories
            val isExpanded = expandedStates[service.quotationServiceId] ?: false
            setupPartCategoriesVisibility(service, isExpanded)

            binding.btnToggleParts.setOnClickListener {
                togglePartCategories(service)
            }
        }

        private fun setupPartCategoriesVisibility(service: QuotationServiceDetail, isExpanded: Boolean) {
            if (isExpanded) {
                // Hiển thị danh sách part categories
                binding.rvPartCategories.visibility = View.VISIBLE
                binding.selectedPartsSummary.visibility = View.GONE
                binding.btnToggleParts.text = "Ẩn phụ tùng"
                binding.btnToggleParts.setIconResource(R.drawable.ic_arrow_drop_up_24dp)

                setupPartCategories(service.partCategories, service)
            } else {
                // Ẩn danh sách, hiển thị summary
                binding.rvPartCategories.visibility = View.GONE
                binding.selectedPartsSummary.visibility = View.VISIBLE
                binding.btnToggleParts.text = "Hiển thị phụ tùng"
                binding.btnToggleParts.setIconResource(R.drawable.ic_arrow_drop_down_24dp)

                setupSelectedPartsSummary(service)
            }
        }

        private fun togglePartCategories(service: QuotationServiceDetail) {
            val serviceId = service.quotationServiceId
            val currentState = expandedStates[serviceId] ?: false
            expandedStates[serviceId] = !currentState
            setupPartCategoriesVisibility(service, !currentState)
        }

        private fun setupSelectedPartsSummary(service: QuotationServiceDetail) {
            val selectedParts = service.partCategories.flatMap { category ->
                category.parts.filter { it.isSelected }
            }

            if (selectedParts.isNotEmpty()) {
                val partsText = selectedParts.joinToString(", ") { it.partName }
                binding.tvSelectedParts.text = partsText
                binding.selectedPartsSummary.visibility = View.VISIBLE
            } else {
                binding.selectedPartsSummary.visibility = View.GONE
            }
        }

        private fun setupPartCategories(partCategories: List<PartCategory>, service: QuotationServiceDetail) {
            if (partCategories.isNotEmpty()) {
                val adapter = PartCategoryAdapter(partCategories, service, onPartToggle, isEditable)
                binding.rvPartCategories.adapter = adapter
                binding.rvPartCategories.layoutManager = LinearLayoutManager(binding.root.context)
            }
        }

        private fun formatCurrency(amount: Double) =
            NumberFormat.getCurrencyInstance(Locale("vi", "VN")).format(amount)
    }
}