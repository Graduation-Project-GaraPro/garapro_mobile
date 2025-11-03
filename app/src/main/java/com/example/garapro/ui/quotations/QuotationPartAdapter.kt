package com.example.garapro.ui.quotations

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.quotations.QuotationServicePart
import com.example.garapro.databinding.ItemQuotationPartBinding
import com.example.garapro.utils.MoneyUtils
import java.text.NumberFormat
import java.util.Locale

class QuotationPartAdapter(
    private val parts: List<QuotationServicePart>,
    private val isEditable: Boolean,
    private val onPartToggle: (String) -> Unit
) : RecyclerView.Adapter<QuotationPartAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQuotationPartBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(parts[position])
    }

    override fun getItemCount() = parts.size

    inner class ViewHolder(private val binding: ItemQuotationPartBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(part: QuotationServicePart) {
            try {
                binding.tvPartName.text = part.partName
                binding.cbPart.isChecked = part.isSelected
                binding.cbPart.isEnabled = isEditable

                // 🔥 HIỂN THỊ TRẠNG THÁI ĐÃ CHỌN
                if (part.isSelected) {
                    binding.root.setBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.selected_part_bg))
                } else {
                    binding.root.setBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.unselected_part_bg))
                }

                // 🔥 QUAN TRỌNG: Remove previous listener to avoid duplicate calls
                binding.cbPart.setOnCheckedChangeListener(null)

                binding.cbPart.setOnCheckedChangeListener { _, isChecked ->
                    // 🔥 QUAN TRỌNG: Only trigger if state actually changed AND is editable
                    if (isChecked != part.isSelected && isEditable) {
                        onPartToggle(part.quotationServicePartId)
                    }
                }

                // 🔥 THÊM: Click trên toàn bộ item cũng trigger toggle
                binding.root.setOnClickListener {
                    if (isEditable) {
                        onPartToggle(part.quotationServicePartId)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}