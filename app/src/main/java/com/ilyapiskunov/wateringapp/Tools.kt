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

object Tools {

    fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    fun Int.modNeg(mod : Int) : Int {
        var ret = this.rem(mod)
        if (ret < 0) ret += mod
        return ret
    }

}