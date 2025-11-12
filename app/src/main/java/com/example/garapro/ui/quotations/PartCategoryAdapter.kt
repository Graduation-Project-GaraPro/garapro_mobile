package com.example.garapro.ui.quotations

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.data.model.quotations.PartCategory
import com.example.garapro.data.model.quotations.QuotationServiceDetail
import com.example.garapro.databinding.ItemQuotationPartCategoryBinding
import java.text.NumberFormat
import java.util.Locale

class PartCategoryAdapter(
    private val partCategories: List<PartCategory>,
    private val service: QuotationServiceDetail,
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

            // 🔥 CẬP NHẬT RULES CHO CHÍNH XÁC
            val selectionRule = if (service.isAdvanced) {
                "Select 1 part in this category - Can select parts from other categories"
            } else {
                "Select 1 part - Automatically unselects other parts in the service"
            }
            binding.tvSelectionRule.text = selectionRule

            // 🔥 HIỂN THỊ TRẠNG THÁI ĐÃ CHỌN


            val adapter = QuotationPartAdapter(
                parts = partCategory.parts,
                isEditable = isEditable
            ) { partId ->
                onPartToggle(service.quotationServiceId, partCategory.partCategoryId, partId)
            }

            binding.rvParts.adapter = adapter
            binding.rvParts.layoutManager = LinearLayoutManager(binding.root.context)
        }
    }
}