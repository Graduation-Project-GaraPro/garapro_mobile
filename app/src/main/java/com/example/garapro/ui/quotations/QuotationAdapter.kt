package com.example.garapro.ui.quotations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.quotations.Quotation
import com.example.garapro.data.model.quotations.QuotationStatus
import com.example.garapro.databinding.ItemQuotationBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class QuotationAdapter(
    private val quotations: List<Quotation>,
    private val onItemClick: (Quotation) -> Unit
) : RecyclerView.Adapter<QuotationAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQuotationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(quotations[position])
    }

    override fun getItemCount() = quotations.size

    inner class ViewHolder(private val binding: ItemQuotationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(quotation: Quotation) {
            binding.tvVehicleInfo.text = quotation.getSafeVehicleInfo()
            binding.tvCustomerName.text = quotation.getSafeCustomerName()
            binding.tvTotalAmount.text = formatCurrency(quotation.totalAmount)
            binding.tvStatus.text = getStatusText(quotation.status)
            binding.tvStatus.setTextColor(getStatusColor(quotation.status))
            binding.tvDate.text = formatDate(quotation.createdAt)

            binding.root.setOnClickListener {
                onItemClick(quotation)
            }
        }

        private fun formatCurrency(amount: Double): String {
            return NumberFormat.getCurrencyInstance(Locale("vi", "VN")).format(amount)
        }

        private fun formatDate(dateString: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                outputFormat.format(date!!)
            } catch (e: Exception) {
                dateString
            }
        }

        private fun getStatusText(status: QuotationStatus): String {
            return when (status) {
                QuotationStatus.Pending -> "Chờ xử lý"
                QuotationStatus.Sent -> "Chưa quyết định"
                QuotationStatus.Approved -> "Đã duyệt"
                QuotationStatus.Rejected -> "Đã từ chối"
                QuotationStatus.Expired -> "Hết hạn"
            }
        }

        private fun getStatusColor(status: QuotationStatus): Int {
            val context = binding.root.context
            return when (status) {
                QuotationStatus.Pending -> ContextCompat.getColor(context, R.color.orange)
                QuotationStatus.Sent -> ContextCompat.getColor(context, R.color.blue)
                QuotationStatus.Approved -> ContextCompat.getColor(context, R.color.green)
                QuotationStatus.Rejected -> ContextCompat.getColor(context, R.color.red)
                QuotationStatus.Expired -> ContextCompat.getColor(context, R.color.gray)
            }
        }
    }
}