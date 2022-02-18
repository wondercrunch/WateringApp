package com.ilyapiskunov.wateringapp

import java.io.ByteArrayOutputStream

const val BUS_ADDRESS = 0x81

class CommandPacket(commandCode : Int) {
    private val cmdStream : ByteArrayOutputStream = ByteArrayOutputStream()
    private val dataStream : ByteArrayOutputStream = ByteArrayOutputStream()

    init {
        cmdStream.write(BUS_ADDRESS)
        cmdStream.write(commandCode)
    }

    fun addByte(b : Int): CommandPacket {
        dataStream.write(b)
        return this
    }

    fun addBytes(bytes: ByteArray): CommandPacket {
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


