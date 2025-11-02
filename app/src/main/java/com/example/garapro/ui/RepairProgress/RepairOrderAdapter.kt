package com.example.garapro.ui.RepairProgress

import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.RepairProgresses.Label
import com.example.garapro.data.model.RepairProgresses.RepairOrderListItem
import com.example.garapro.databinding.ItemRepairOrderBinding
import com.google.android.material.chip.Chip
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class RepairOrderAdapter(
    private val onItemClick: (RepairOrderListItem) -> Unit
) : ListAdapter<RepairOrderListItem, RepairOrderAdapter.ViewHolder>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<RepairOrderListItem>() {
            override fun areItemsTheSame(oldItem: RepairOrderListItem, newItem: RepairOrderListItem): Boolean {
                return oldItem.repairOrderId == newItem.repairOrderId
            }

            override fun areContentsTheSame(oldItem: RepairOrderListItem, newItem: RepairOrderListItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRepairOrderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(private val binding: ItemRepairOrderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(item: RepairOrderListItem) {
            binding.apply {
                vehicleInfo.text = "${item.vehicleLicensePlate} • ${item.vehicleModel}"
                receiveDate.text = formatDate(item.receiveDate)
                statusText.text = getStatusNameVietnamese(item.statusName)
                progressStatus.text = item.progressStatus
                progressPercentage.text = (item.progressPercentage?.toString() ?: "0") + "%"
                progressBar.progress = item.progressPercentage
                costText.text = formatCurrency(item.cost)
                paidStatusText.text = getPaidStatusVietnamese(item.paidStatus)
                roTypeText.text = getROTypeNameVietnamese(item.roType)
                Log.d("progressPercentage",item.progressPercentage.toString())
                // Set status color
                val statusColor = getStatusColor(item.statusName)
//                statusText.setTextColor(statusColor)
                statusText.setBackgroundColor(statusColor)

                // Setup labels
                setupLabels(item.labels)
            }
        }

        private fun setupLabels(labels: List<Label>) {
//            binding.labelsContainer.removeAllViews()
//            labels.forEach { label ->
//                val chip = Chip(binding.root.context).apply {
//                    text = label.labelName
//                    setTextColor(Color.parseColor(label.hexCode))
//                    chipStrokeColor = ColorStateList.valueOf(Color.parseColor(label.hexCode))
//                    chipStrokeWidth = 1f
//                    setChipBackgroundColorResource(android.R.color.transparent)
//                }
//                binding.labelsContainer.addView(chip)
//            }
        }

        private fun formatDate(dateString: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
                val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                outputFormat.format(date!!)
            } catch (e: Exception) {
                dateString
            }
        }

        private fun formatCurrency(amount: Double): String {
            return NumberFormat.getCurrencyInstance(Locale("vi", "VN")).format(amount)
        }

        private fun getStatusColor(statusName: String): Int {
            return when (statusName) {
                "Completed" -> ContextCompat.getColor(binding.root.context, R.color.green)
                "In Progress" -> ContextCompat.getColor(binding.root.context, R.color.blue)
                "Pending" -> ContextCompat.getColor(binding.root.context, R.color.orange)
                else -> ContextCompat.getColor(binding.root.context, R.color.gray)
            }
        }
        private fun getStatusNameVietnamese(statusName: String): String{
            return when (statusName) {
                "Completed" -> "Hoàn tất"
                "In Progress" -> "Đang sửa"
                "Pending" -> "Đang xử lý"
                else -> "Không xác định"
            }
        }
        private fun getPaidStatusVietnamese(statusName: String): String{
            return when (statusName) {
                "Paid" -> "Đã thanh toán"
                "Partial" -> "Trả 1 phần"
                "Pending" -> "chưa thanh toán"
                else -> "Không xác định"
            }
        }

        private fun getROTypeNameVietnamese(statusName: String): String{
            return when (statusName) {
                "WalkIn" -> "vãng lai"
                "Scheduled" -> "Đặt lịch"
                "Breakdown" -> "Sự cố"
                else -> "Không xác định"
            }
        }
    }
}