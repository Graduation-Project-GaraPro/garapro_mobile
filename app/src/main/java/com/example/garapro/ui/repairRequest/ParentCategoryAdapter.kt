package com.example.garapro.ui.repairRequest

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.repairRequest.ParentServiceCategory
import com.google.android.material.card.MaterialCardView

class ParentCategoryAdapter(
    private var categories: List<ParentServiceCategory>,
    private val onCategorySelected: (ParentServiceCategory) -> Unit
) : RecyclerView.Adapter<ParentCategoryAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCategoryName: TextView = itemView.findViewById(R.id.tvCategoryName)
        val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)

        val tvChildCount : TextView = itemView.findViewById(R.id.tvChildCount)
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardCategory)

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_parent_booking_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        holder.tvCategoryName.text = category.categoryName
        holder.tvDescription.text = category.description

        holder.tvChildCount.text = category.childCategories?.size.toString() + " Loại dịch vụ đang có";
        holder.cardView.setOnClickListener {
            Log.d("ParentTochild","toggle");
            onCategorySelected(category)
        }
    }

    override fun getItemCount() = categories.size

    fun updateData(newCategories: List<ParentServiceCategory>) {
        categories = newCategories
        notifyDataSetChanged()
    }
}