package com.ilyapiskunov.wateringapp

import com.ilyapiskunov.wateringapp.exception.DeviceException
import java.util.*

class Response(payload : ByteArray) {
    val status : Int = payload[1].toInt()
    val data : ByteArray
    init {
        val dataLength = payload[2]
        data = if (dataLength > 0)
            payload.copyOfRange(3, 3 + dataLength)
        else
            byteArrayOf()
    }

    fun checkStatus() : Response{
        if (status != 0) throw DeviceException(status)
        return this
    }

    fun checkData() : Response {
        if (data.isEmpty()) throw DeviceException(0x42)
        return this
    }

}
