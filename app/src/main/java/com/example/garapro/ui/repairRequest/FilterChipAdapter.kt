package com.example.garapro.ui.repairRequest

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.repairRequest.ServiceCategory
import com.google.android.material.chip.Chip
import android.content.res.ColorStateList
class FilterChipAdapter(
    private var categories: List<ServiceCategory>,
    private var currentFilterCategoryId: String?,
    private val onFilterSelected: (String?) -> Unit
) : RecyclerView.Adapter<FilterChipAdapter.FilterChipViewHolder>() {

    class FilterChipViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val chip: Chip = itemView.findViewById(R.id.chipFilter)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterChipViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_filter_chip, parent, false)
        return FilterChipViewHolder(view)
    }

    override fun onBindViewHolder(holder: FilterChipViewHolder, position: Int) {
        val availableCategories = getAvailableCategories()
        val category = availableCategories[position]

        holder.chip.text = category.categoryName
        holder.chip.setOnClickListener {
            onFilterSelected(category.serviceCategoryId)
        }
    }

    override fun getItemCount(): Int = getAvailableCategories().size

    private fun getAvailableCategories(): List<ServiceCategory> {
        return if (currentFilterCategoryId == null) {
            // Khi đang "Tất cả": hiển thị tất cả categories
            categories
        } else {
            // Khi có filter đang chọn: ẩn cái đó đi
            categories.filter { it.serviceCategoryId != currentFilterCategoryId }
        }
    }

    fun updateData(newCategories: List<ServiceCategory>, newCurrentFilterId: String?) {
        categories = newCategories
        currentFilterCategoryId = newCurrentFilterId
        notifyDataSetChanged()

        Log.d("FilterChip", "📊 Showing ${getAvailableCategories().size} of ${categories.size} categories")
    }
}