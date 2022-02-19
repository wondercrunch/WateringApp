package com.ilyapiskunov.wateringapp

import android.bluetooth.BluetoothDevice
import com.ilyapiskunov.wateringapp.exception.DeviceException
import com.ilyapiskunov.wateringapp.exception.TimeoutException
import java.nio.charset.StandardCharsets

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

const val MIN_RESPONSE_SIZE = 4
const val CMD_GET_CONFIG = 0x86

class Device(val bluetoothDevice : BluetoothDevice) {
    val name : String
    private val responseQueue : BlockingQueue<Response> = LinkedBlockingQueue()
    private val connectionEventListener : ConnectionEventListener = ConnectionEventListener().apply {
        onRead = {
            device, payload ->
            if (device == bluetoothDevice && payload.size >= MIN_RESPONSE_SIZE) {
                responseQueue.put(Response(payload))
            }
        }
    }
    private val channels : List<Channel>

    init {

        ConnectionManager.registerListener(connectionEventListener)
        val identify = CommandPacket(0x85).toByteArray()
        ConnectionManager.write(bluetoothDevice, identify)
        val nameResponse = responseQueue.poll(4000, TimeUnit.MILLISECONDS) ?: throw TimeoutException()
        nameResponse.checkStatus()
        name = String(nameResponse.data, StandardCharsets.UTF_8)
        val readChannelsCount = CommandPacket(0x86).addByte(1).toByteArray()
        ConnectionManager.write(bluetoothDevice, readChannelsCount)
        val countResponse = responseQueue.poll(4000, TimeUnit.MILLISECONDS) ?: throw TimeoutException()
        countResponse.checkStatus().checkData()
        val channelsCount = countResponse.data[0].toInt()
        channels = ArrayList(channelsCount)
    }

}