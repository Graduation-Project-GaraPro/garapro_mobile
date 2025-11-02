package com.example.garapro.ui.quotations

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.data.model.quotations.PartCategory
import com.example.garapro.data.model.quotations.QuotationServiceDetail
import com.example.garapro.databinding.ItemQuotationServiceBinding
import java.text.NumberFormat
import java.util.Locale

class QuotationServiceAdapter(
    private var services: List<QuotationServiceDetail>,
    private var onCheckChanged: (String, Boolean) -> Unit,
    private var onPartToggle: (String, String, String) -> Unit, // 🔥 THÊM: callback cho part
    private var isEditable: Boolean = true
) : RecyclerView.Adapter<QuotationServiceAdapter.ViewHolder>() {

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

            // 🔥 THÊM: Hiển thị "Bắt buộc" nếu service là required
            if (service.isRequired) {
                binding.tvRequired.visibility = View.VISIBLE
                binding.tvRequired.text = "Bắt buộc"
            } else {
                binding.tvRequired.visibility = View.GONE
            }

            // Vô hiệu hóa checkbox khi không được chỉnh sửa HOẶC service là required
            val canToggleService = isEditable && !service.isRequired
            binding.cbService.isEnabled = canToggleService

            binding.cbService.setOnCheckedChangeListener(null)
            binding.cbService.isChecked = service.isSelected

            if (isEditable && canToggleService) {
                binding.cbService.setOnCheckedChangeListener { _, isChecked ->
                    onCheckChanged(service.quotationServiceId, isChecked)
                }
            } else {
                binding.cbService.setOnCheckedChangeListener(null)
            }

            // 🔥 THAY ĐỔI: Setup part categories thay vì parts
            setupPartCategories(service.partCategories, service.quotationServiceId)
        }

        private fun setupPartCategories(partCategories: List<PartCategory>, serviceId: String) {
            if (partCategories.isNotEmpty()) {
                binding.rvPartCategories.visibility = View.VISIBLE
                val adapter = PartCategoryAdapter(partCategories, serviceId, onPartToggle, isEditable)
                binding.rvPartCategories.adapter = adapter
                binding.rvPartCategories.layoutManager = LinearLayoutManager(binding.root.context)
            } else {
                binding.rvPartCategories.visibility = View.GONE
            }
        }

        private fun formatCurrency(amount: Double) =
            NumberFormat.getCurrencyInstance(Locale("vi", "VN")).format(amount)
    }
}