package com.ilyapiskunov.wateringapp

import android.util.Log
import com.ilyapiskunov.wateringapp.Tools.toHex
import com.ilyapiskunov.wateringapp.exception.DeviceException
import java.util.*

class Response(val status: Int, val data: ByteArray) {

    fun checkStatus() : Response {
        if (status != 0) throw DeviceException(status)
        return this
    }

    fun checkData() : Response {
        if (data.isEmpty()) throw DeviceException(0x42)
        return this
    }

    override fun toString(): String {
        return "status=${status.toString(16)}, data=${data.toHex()}"
    }


}
