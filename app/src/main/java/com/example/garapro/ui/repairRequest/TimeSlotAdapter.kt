package com.example.garapro.ui.repairRequest

import android.graphics.Color
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.data.model.repairRequest.ArrivalWindow

class TimeSlotAdapter(
    private var items: List<ArrivalWindow>,
    private val onSelect: (ArrivalWindow) -> Unit
) : RecyclerView.Adapter<TimeSlotAdapter.VH>() {

    private var selectedPos: Int = RecyclerView.NO_POSITION

    inner class VH(val btn: com.google.android.material.button.MaterialButton) :
        RecyclerView.ViewHolder(btn) {
        init {
            btn.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val item = items[pos]
                    if (!item.isFull && item.remaining > 0) {
                        notifyItemChanged(selectedPos)
                        selectedPos = pos
                        notifyItemChanged(selectedPos)
                        onSelect(item)
                    }
                }
            }
        }
    }
    fun setSelectedIndex(index: Int) {
        if (index in 0 until items.size) {
            if (selectedPos != RecyclerView.NO_POSITION) notifyItemChanged(selectedPos)
            selectedPos = index
            notifyItemChanged(selectedPos)
        }
    }

    /** Preselect theo yyyy-MM-dd và HH:mm (không cần đúng giây/múi giờ) */
    fun setSelectedByDateHm(date: String, hm: String) {
        val prefix = "$date"+"T"+"$hm" // ví dụ "2025-11-07T08:00"
        val idx = items.indexOfFirst { it.windowStart.startsWith(prefix) }
        if (idx >= 0) setSelectedIndex(idx)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = com.google.android.material.button.MaterialButton(parent.context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { (it as ViewGroup.MarginLayoutParams).setMargins(8, 8, 8, 8) }
            insetTop = 8; insetBottom = 8
            cornerRadius = 24
            isAllCaps = false
        }
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        // hiển thị HH:mm - HH:mm (remaining)
        fun toHm(iso: String): String {
            // simple parse: lấy HH:mm từ "yyyy-MM-ddTHH:mm:ss±hh:mm"
            val tIndex = iso.indexOf('T')
            val timePart = if (tIndex != -1) iso.substring(tIndex + 1) else iso
            return timePart.substring(0,5) // HH:mm
        }

        val label = "${toHm(item.windowStart)} - ${toHm(item.windowEnd)}  (${item.remaining}/${item.capacity})"
        holder.btn.text = label

        val disabled = item.isFull || item.remaining <= 0
        holder.btn.isEnabled = !disabled

        // style chọn/không chọn
        if (position == selectedPos) {
            holder.btn.setBackgroundColor(Color.BLACK)
            holder.btn.setTextColor(Color.WHITE)
        } else {
            holder.btn.setBackgroundColor(Color.parseColor("#FFFFFF"))
            holder.btn.setStrokeColorResource(android.R.color.darker_gray)
            holder.btn.strokeWidth = 2
            holder.btn.setTextColor(if (disabled) Color.parseColor("#9E9E9E") else Color.BLACK)
        }
        holder.btn.alpha = if (disabled) 0.5f else 1f
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<ArrivalWindow>) {
        items = newItems
        selectedPos = RecyclerView.NO_POSITION
        notifyDataSetChanged()
    }
}
