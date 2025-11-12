package com.example.garapro.ui.RepairProgress

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.RepairProgresses.Part
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.NumberFormat
import java.util.Locale

class PartAdapter : ListAdapter<Part, PartAdapter.ViewHolder>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Part>() {
            override fun areItemsTheSame(oldItem: Part, newItem: Part): Boolean {
                return oldItem.partId == newItem.partId
            }

            override fun areContentsTheSame(oldItem: Part, newItem: Part): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_part_repair_progress, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val part = getItem(position)
        holder.bind(part)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val chip: Chip = itemView as Chip

        fun bind(part: Part) {
            chip.text = "${part.name}"

            chip.setOnClickListener {
                showPartDetails(part)
            }
        }

        private fun showPartDetails(part: Part) {
            val message = buildString {
                append("Unit Price: ${formatCurrency(part.price)}")
            }

            MaterialAlertDialogBuilder(itemView.context)
                .setTitle(part.name)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }

        private fun formatCurrency(amount: Double): String {
            return NumberFormat.getCurrencyInstance(Locale("vi", "VN")).format(amount)
        }
    }
}