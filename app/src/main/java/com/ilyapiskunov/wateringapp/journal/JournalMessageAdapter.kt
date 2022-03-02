package com.ilyapiskunov.wateringapp.journal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ilyapiskunov.wateringapp.R
import kotlinx.android.synthetic.main.journal_message.view.*

class JournalMessageAdapter(private val messages : List<JournalMessage>)
    : RecyclerView.Adapter<JournalMessageAdapter.MessageHolder>() {

    inner class MessageHolder(messageView : View) : RecyclerView.ViewHolder(messageView) {
        val tvTimestamp : TextView = messageView.tv_timestamp
        val tvHeader : TextView = messageView.tv_header
        val tvText : TextView = messageView.tv_text
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageHolder {
        val messageView = LayoutInflater.from(parent.context)
            .inflate(R.layout.journal_message, parent, false)
        return MessageHolder(messageView)
    }

    override fun onBindViewHolder(holder: MessageHolder, position: Int) {
        val message = messages[position]
        holder.tvTimestamp.text = message.timestamp
        holder.tvHeader.text = message.header
        holder.tvText.text = message.text
    }

    override fun getItemCount(): Int {
        return messages.size
    }
}