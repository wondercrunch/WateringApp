package com.ilyapiskunov.wateringapp.journal

import android.app.Activity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.ilyapiskunov.wateringapp.R
import kotlinx.android.synthetic.main.device_list_layout.*
import kotlinx.android.synthetic.main.journal_layout.*
import java.util.concurrent.LinkedBlockingQueue

class JournalActivity : Activity() {

    private val messages = ArrayList<JournalMessage>()
    private val messageAdapter = JournalMessageAdapter(messages)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.journal_layout)
        list_messages.adapter = messageAdapter
        list_messages.layoutManager = LinearLayoutManager(this)
    }

    override fun onStart() {
        super.onStart()
        val newMessages : ArrayList<JournalMessage> = intent.getSerializableExtra("messages") as ArrayList<JournalMessage>
        messages.addAll(newMessages)
    }
}