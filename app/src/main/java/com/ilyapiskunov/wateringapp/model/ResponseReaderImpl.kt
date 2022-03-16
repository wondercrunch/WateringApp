package com.ilyapiskunov.wateringapp.model

import com.ilyapiskunov.wateringapp.CRCUtils

import java.io.ByteArrayInputStream

class ResponseReaderImpl : ResponseReader {
    private val ERROR_CRC = 0x03
    private enum class State {
        WAITING,
        PREFIX_RECEIVED,
        DATA_RECEIVED
    }


    private var state = State.WAITING
    private var buffer = ByteArray(PacketFormat.PREFIX_LENGTH)
    private var dataLength = 0
    private var insertIndex = 0

    override fun read(payload : ByteArray) : Response? {
        val bytes = ByteArrayInputStream(payload)
        while (bytes.available() > 0) {
            buffer[insertIndex++] = bytes.read().toByte()
            if (insertIndex == buffer.size) {
                when (state) {
                    State.WAITING -> {
                        dataLength = buffer.getValue(PacketFormat.DATA_LENGTH_OFFSET, PacketFormat.DATA_LENGTH_BYTE_SIZE)
                        buffer = buffer.copyOf(PacketFormat.PREFIX_LENGTH + dataLength + PacketFormat.CRC_BYTE_SIZE /* CRC8 byte */)
                        state = State.PREFIX_RECEIVED

                    }
                    State.PREFIX_RECEIVED -> {
                        val crc = buffer.getValue(buffer.size - PacketFormat.CRC_BYTE_SIZE, PacketFormat.CRC_BYTE_SIZE).toByte()
                        val calculatedCrc =
                            PacketFormat.getCRC(buffer, buffer.size - 1).toByte()
                        val status : Int = if (crc != calculatedCrc) ERROR_CRC else (buffer[PacketFormat.STATUS_OFFSET].toInt() and 0xFF)
                        val data =
                            if (dataLength == 0) byteArrayOf()
                            else buffer.copyOfRange(
                                PacketFormat.PREFIX_LENGTH,
                                PacketFormat.PREFIX_LENGTH + dataLength
                            )
                        reset()
                        return Response(status, data)

                    }

                    State.DATA_RECEIVED -> {

                    }
                }
            }
        }
        return null
    }

    //LSB first
    private fun ByteArray.getValue(offset : Int, byteSize : Int) : Int {
        var res = 0
        for (i in 0 until byteSize) {
            res += ((this[i + offset].toInt() and 0xFF) shl 8*i)
        }
        return res
    }



    override fun reset() {
        state = State.WAITING
        insertIndex = 0
        dataLength = 0
        buffer = ByteArray(PacketFormat.PREFIX_LENGTH)
    }


}