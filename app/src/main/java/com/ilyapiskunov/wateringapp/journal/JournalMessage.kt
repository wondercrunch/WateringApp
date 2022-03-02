package com.ilyapiskunov.wateringapp.journal

import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.*

data class JournalMessage(val header : String, val text : String) : Serializable {
    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
}
