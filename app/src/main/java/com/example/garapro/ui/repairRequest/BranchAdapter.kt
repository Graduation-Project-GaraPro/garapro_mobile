package com.example.garapro.ui.repairRequest

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.repairRequest.Branch
import com.google.android.material.card.MaterialCardView

class BranchAdapter(
    private var branches: List<Branch>,
    private val onBranchSelected: (Branch) -> Unit
) : RecyclerView.Adapter<BranchAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvBranchName: TextView = itemView.findViewById(R.id.tvBranchName)
        val tvBranchAddress: TextView = itemView.findViewById(R.id.tvBranchAddress)
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardBranch)
    }

    private var selectedPosition = RecyclerView.NO_POSITION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_booking_branch, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val branch = branches[position]

        holder.tvBranchName.text = branch.branchName
        holder.tvBranchAddress.text = "${branch.district}, ${branch.city}"

        // Highlight selected item
        val colorRes = if (position == selectedPosition) R.color.primary_color else R.color.white
        holder.cardView.setCardBackgroundColor(
            ContextCompat.getColor(holder.itemView.context, colorRes)
        )

        holder.cardView.setOnClickListener {
            val currentPosition = holder.adapterPosition
            if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener

            val previousPosition = selectedPosition
            selectedPosition = currentPosition

            if (previousPosition != RecyclerView.NO_POSITION) notifyItemChanged(previousPosition)
            notifyItemChanged(selectedPosition)

            onBranchSelected(branches[selectedPosition])
        }
    }

    override fun getItemCount() = branches.size

    fun updateData(newBranches: List<Branch>) {
        branches = newBranches
        notifyDataSetChanged()
    }

    // 👇 Thêm 2 hàm này để đồng bộ branch được chọn
    fun getPositionOf(branch: Branch): Int {
        return branches.indexOfFirst { it.branchId == branch.branchId }
    }

    fun setSelectedPosition(position: Int) {
        val previousPosition = selectedPosition
        selectedPosition = position
        if (previousPosition != RecyclerView.NO_POSITION) notifyItemChanged(previousPosition)
        if (selectedPosition != RecyclerView.NO_POSITION) notifyItemChanged(selectedPosition)
    }
}
