package com.ilyapiskunov.wateringapp.model

import com.ilyapiskunov.wateringapp.CRCUtils
import java.io.ByteArrayOutputStream

class CommandPacket(commandCode : Int) {
    private val DUMMY_BYTE = 0x81
    private val cmdStream : ByteArrayOutputStream = ByteArrayOutputStream()
    private val dataStream : ByteArrayOutputStream = ByteArrayOutputStream()

    init {
        cmdStream.write(DUMMY_BYTE)
        cmdStream.write(commandCode)
    }

    fun add(b : Int): CommandPacket {
        dataStream.write(b)
        return this
    }

    fun add(bytes: ByteArray): CommandPacket {
        dataStream.write(bytes)
        return this
    }



    fun toByteArray(): ByteArray {
        val dataLength = dataStream.size()
        cmdStream.write(dataLength)
        if (dataLength > 0) {
            cmdStream.write(dataStream.toByteArray())
        }
        val crc8 = CRCUtils.getCRC8(cmdStream.toByteArray())
        cmdStream.write(crc8)
        return cmdStream.toByteArray()
    }
}


