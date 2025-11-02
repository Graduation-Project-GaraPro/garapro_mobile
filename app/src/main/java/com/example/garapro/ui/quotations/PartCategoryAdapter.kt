package com.example.garapro.ui.quotations

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.data.model.quotations.PartCategory
import com.example.garapro.databinding.ItemQuotationPartCategoryBinding
import java.text.NumberFormat
import java.util.Locale

class PartCategoryAdapter(
    private val partCategories: List<PartCategory>,
    private val serviceId: String,
    private val onPartToggle: (String, String, String) -> Unit,
    private val isEditable: Boolean = true
) : RecyclerView.Adapter<PartCategoryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemQuotationPartCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(partCategories[position])
    }

    override fun getItemCount() = partCategories.size

    inner class ViewHolder(private val binding: ItemQuotationPartCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(partCategory: PartCategory) {
            binding.tvCategoryName.text = partCategory.partCategoryName

            // 🔥 HIỂN THỊ RULES MỚI
            val selectionRule = if (partCategory.isAdvanced) {
                "Bắt buộc chọn 1 part - Có thể chọn part khác category"
            } else {
                "Bắt buộc chọn 1 part - Không thể chọn part trùng category khác"
            }
            binding.tvSelectionRule.text = selectionRule

            // 🔥 HIỂN THỊ TRẠNG THÁI ĐÃ CHỌN
            val selectedPart = partCategory.parts.find { it.isSelected }
            if (selectedPart != null) {
                binding.tvSelectedPart.text = "Đã chọn: ${selectedPart.partName}"
                binding.tvSelectedPart.visibility = View.VISIBLE
            } else {
                binding.tvSelectedPart.visibility = View.GONE
            }

            val adapter = QuotationPartAdapter(
                parts = partCategory.parts,
                isEditable = isEditable
            ) { partId ->
                onPartToggle(serviceId, partCategory.partCategoryId, partId)
            }

            binding.rvParts.adapter = adapter
            binding.rvParts.layoutManager = LinearLayoutManager(binding.root.context)
        }
    }
}