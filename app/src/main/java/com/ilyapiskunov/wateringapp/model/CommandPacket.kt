package com.ilyapiskunov.wateringapp.model

import com.ilyapiskunov.wateringapp.CRCUtils
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class CommandPacket(commandCode : Int) {
    private val cmdStream : ByteArrayOutputStream = ByteArrayOutputStream()
    private val dataStream : ByteArrayOutputStream = ByteArrayOutputStream()

    init {
        cmdStream.write(PacketFormat.TX_BUS_ADDRESS)
        cmdStream.write(commandCode)
    }

    fun put(byte : Int): CommandPacket {
        dataStream.write(byte)
        return this
    }

    fun put(bytes: ByteArray): CommandPacket {
        dataStream.write(bytes)
        return this
    }

    //LSB first
    private fun ByteArrayOutputStream.putValue(value : Int, byteSize : Int) {
        for (i in 0 until byteSize)
            this.write((value ushr 8*i) and 0xFF)
    }

    fun put(value : Int, byteSize : Int) {
        dataStream.putValue(value, byteSize)
    }

    fun toByteArray(): ByteArray {
        val dataLength = dataStream.size()
        cmdStream.putValue(dataLength, PacketFormat.DATA_LENGTH_BYTE_SIZE)
        if (dataLength > 0) {
            cmdStream.write(dataStream.toByteArray())
        }
        val crc = PacketFormat.getCRC(cmdStream.toByteArray())
        cmdStream.putValue(crc, PacketFormat.CRC_BYTE_SIZE)
        return cmdStream.toByteArray()
    }
}


