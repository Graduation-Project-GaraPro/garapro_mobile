package com.example.garapro.ui.repairRequest

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.garapro.R

class ImageAdapter(
    private var imageUris: MutableList<Uri>,
    private val onRemoveImage: (Uri) -> Unit,
    private val isReadOnly: Boolean = false
) : RecyclerView.Adapter<ImageAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.ivImage)
        val btnRemove: ImageButton = itemView.findViewById(R.id.btnRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_booking_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val uri = imageUris[position]

        Glide.with(holder.itemView.context)
            .load(uri)
            .centerCrop()
            .into(holder.imageView)

        if (isReadOnly) {
            holder.btnRemove.visibility = View.GONE
        } else {
            holder.btnRemove.visibility = View.VISIBLE
            holder.btnRemove.setOnClickListener {
                onRemoveImage(uri)
                notifyDataSetChanged()
            }
        }
    }

    override fun getItemCount() = imageUris.size

    fun updateData(newUris: MutableList<Uri>) {
        imageUris = newUris
        notifyDataSetChanged()
    }
}