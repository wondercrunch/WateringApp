package com.ilyapiskunov.wateringapp

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import org.jetbrains.anko.alert
import java.util.*

object Tools {

    fun ByteArray.toHex(separator : String): String = joinToString(separator = separator) { eachByte -> "%02x".format(eachByte) }

}