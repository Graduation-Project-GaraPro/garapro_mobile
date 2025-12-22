package com.example.garapro.ui.RepairProgress.archived

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.data.model.RepairProgresses.ArchivedJobPart
import com.example.garapro.databinding.ItemArchivedJobPartBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class ArchivedJobPartAdapter(
    private val items: List<ArchivedJobPart>
) : RecyclerView.Adapter<ArchivedJobPartAdapter.ViewHolder>() {

    inner class ViewHolder(
        private val binding: ItemArchivedJobPartBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ArchivedJobPart) = with(binding) {
            tvPartName.text = item.partName
            tvPartInfo.text = "x${item.quantity} • ${formatCurrency(item.unitPrice)} • ${formatCurrency(item.lineTotal)}"


                if (item.warrantyMonths != null) {
                  tvwarrantyMonths.text = "Warranty: ${item.warrantyMonths} month" + " (${formatIsoDate(item.warrantyStartAt)} -> ${formatIsoDate(item.warrantyEndAt)})"

                }else
                {
                    tvwarrantyMonths.visibility = View.GONE;
                }

        }

        private fun formatCurrency(amount: Double): String {
            return String.format("%,.0f đ", amount)
        }
    }

    private fun formatIsoDate(dateString: String?): String {
        if (dateString.isNullOrEmpty()) return "-"

        return try {
            val inputFormat = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS",
                Locale.getDefault()
            )
            val outputFormat = SimpleDateFormat(
                "dd-MM-yyyy",
                Locale.getDefault()
            )
            val date = inputFormat.parse(dateString)
            outputFormat.format(date!!)
        } catch (e: Exception) {
            "-"
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemArchivedJobPartBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }
}
