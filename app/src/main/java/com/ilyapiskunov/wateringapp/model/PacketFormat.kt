package com.ilyapiskunov.wateringapp.model

import com.ilyapiskunov.wateringapp.CRCUtils
import java.nio.charset.Charset

object PacketFormat {

    const val TX_BUS_ADDRESS = 0x81
    const val RX_BUS_ADDRESS = 0x01

    const val PREFIX_LENGTH = 3
    const val STATUS_OFFSET = 1
    const val DATA_LENGTH_OFFSET = 2
    const val DATA_LENGTH_BYTE_SIZE = 1
    const val CRC_BYTE_SIZE = 1

    const val DEVICE_NAME_MAX_BYTE_SIZE = 20

    fun getCRC(bytes : ByteArray, length : Int = bytes.size) : Int {
        return CRCUtils.getCRC8(bytes, length)
    }

    fun getCharset() : Charset {
        return Charset.forName("KOI8-R")
    }

}