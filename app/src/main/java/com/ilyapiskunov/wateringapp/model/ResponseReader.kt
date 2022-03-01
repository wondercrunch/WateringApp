package com.ilyapiskunov.wateringapp.model

import com.ilyapiskunov.wateringapp.CRCUtils
import java.io.ByteArrayInputStream
import java.util.concurrent.BlockingQueue

class ResponseReader(private val responseQueue: BlockingQueue<Response>) {

    private enum class State {
        WAITING,
        PREFIX_RECEIVED,
        DATA_RECEIVED
    }
    private val PREFIX_LENGTH = 3
    private val DATA_LENGTH_OFFSET = 2
    private val DATA_LENGTH_BYTE_SIZE = 1
    private val CRC_BYTE_SIZE = 1

    private var state = State.WAITING
    private var buffer = ByteArray(PREFIX_LENGTH)
    private var dataLength = 0
    private var insertIndex = 0

    fun handle(payload : ByteArray) {
        val bytes = ByteArrayInputStream(payload)
        while (bytes.available() > 0) {
            buffer[insertIndex++] = bytes.read().toByte()
            if (insertIndex == buffer.size) {
                when (state) {
                    State.WAITING -> {
                        dataLength = extractValue(DATA_LENGTH_OFFSET, DATA_LENGTH_BYTE_SIZE)
                        buffer = buffer.copyOf(PREFIX_LENGTH + dataLength + CRC_BYTE_SIZE /* CRC8 byte */)
                        state = State.PREFIX_RECEIVED

                    }
                    State.PREFIX_RECEIVED -> {
                        val crc = extractValue(buffer.size - CRC_BYTE_SIZE, CRC_BYTE_SIZE).toByte()
                        val calculatedCrc =
                            CRCUtils.getCRC8(buffer, buffer.size - 1).toByte()
                        val status = if (crc != calculatedCrc) ERROR_CRC else buffer[1]
                        val data =
                            if (dataLength == 0) byteArrayOf() else buffer.copyOfRange(
                                PREFIX_LENGTH,
                                PREFIX_LENGTH + dataLength
                            )
                        responseQueue.put(Response(status.toInt(), data))
                        reset()

                    }

                    State.DATA_RECEIVED -> {
                        //calc crc?
                    }
                }
            }
        }
    }

    //MSB first
    private fun extractValue(offset : Int, length : Int) : Int {
        var res = 0
        for (i in offset .. offset + length) {
            res = res shl 8
            res += buffer[i]
        }
        return res
    }



    fun reset() {
        state = State.WAITING
        insertIndex = 0
        dataLength = 0
        buffer = ByteArray(PREFIX_LENGTH)
    }


}