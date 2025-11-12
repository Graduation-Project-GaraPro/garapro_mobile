package com.example.garapro.ui.emergencies

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.emergencies.Garage
import com.google.android.material.card.MaterialCardView

class GarageAdapter(
    private val onGarageSelected: (Garage) -> Unit
) : ListAdapter<Garage, GarageAdapter.GarageViewHolder>(GarageDiffCallback()) {

    private var selectedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GarageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_garage_emergency, parent, false)
        return GarageViewHolder(view)
    }

    override fun onBindViewHolder(holder: GarageViewHolder, position: Int) {
        val garage = getItem(position)
        holder.bind(garage, position == selectedPosition)

        holder.itemView.setOnClickListener {
            val previousPosition = selectedPosition
            selectedPosition = holder.adapterPosition
            if (previousPosition != -1) {
                notifyItemChanged(previousPosition)
            }
            notifyItemChanged(selectedPosition)
            onGarageSelected(garage)
        }
    }

    fun clearSelection() {
        val previousPosition = selectedPosition
        selectedPosition = -1
        if (previousPosition != -1) {
            notifyItemChanged(previousPosition)
        }
    }

    inner class GarageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
        private val ivGarageIcon: ImageView = itemView.findViewById(R.id.ivGarageIcon)
        private val tvGarageName: TextView = itemView.findViewById(R.id.tvGarageName)
        private val tvAddress: TextView = itemView.findViewById(R.id.tvAddress)
        private val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        private val tvRating: TextView = itemView.findViewById(R.id.tvRating)

        fun bind(garage: Garage, isSelected: Boolean) {
            tvGarageName.text = garage.name
            tvAddress.text = garage.address
            tvDistance.text = "${garage.distance.formatDistance()} km"
            tvPrice.text = garage.price.formatPrice()
            tvRating.text = garage.rating.toString()

            // Update selection state
            if (isSelected) {
                cardView.strokeColor = ContextCompat.getColor(itemView.context, R.color.primary)
                cardView.strokeWidth = 4
                cardView.cardElevation = 8f
            } else {
                cardView.strokeColor = ContextCompat.getColor(itemView.context, android.R.color.transparent)
                cardView.strokeWidth = 0
                cardView.cardElevation = 2f
            }
        }
    }
}

class GarageDiffCallback : DiffUtil.ItemCallback<Garage>() {
    override fun areItemsTheSame(oldItem: Garage, newItem: Garage): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Garage, newItem: Garage): Boolean {
        return oldItem == newItem
    }
}

// Extension functions

fun Double.formatDistance(): String = "%.1f".format(this)