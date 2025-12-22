package com.example.garapro.ui.chat

import android.view.LayoutInflater
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.Message

class MessageAdapter(private val messages: MutableList<Message>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_ME = 1
    private val TYPE_OTHER = 2

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isMe) TYPE_ME else TYPE_OTHER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_ME) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_me, parent, false)
            MeViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_other, parent, false)
            OtherViewHolder(view)
        }
    }

    override fun getItemCount() = messages.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        if (holder is MeViewHolder) holder.bind(msg)
        if (holder is OtherViewHolder) holder.bind(msg)
    }

    fun addMessage(msg: Message) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    class MeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(msg: Message) {
            itemView.findViewById<TextView>(R.id.txtMessageMe).text = msg.text
        }
    }

    class OtherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind(msg: Message) {
            val tv = itemView.findViewById<TextView>(R.id.txtMessageOther)

            tv.isSingleLine = false
            tv.maxLines = Integer.MAX_VALUE
            tv.ellipsize = null

            tv.text = msg.text
                .replace("\\n", "\n")
                .replace("/n", "\n")
        }
    }
}
