package com.ilyapiskunov.wateringapp

import android.bluetooth.BluetoothDevice
import com.ilyapiskunov.wateringapp.exception.ConnectionException
import com.ilyapiskunov.wateringapp.exception.TimeoutException
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.*

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

const val MIN_RESPONSE_SIZE = 4
const val CMD_READ_CONFIG = 0x86
const val CMD_IDENTIFY = 0x8A
const val CMD_GET_STATE = 0x6E
const val DEFAULT_TIMEOUT = 4000L
class SprinklerDevice(val bluetoothDevice : BluetoothDevice) {
    val waterLevel : Int
    val voltage : Float
    val name : String
    private val responseQueue : BlockingQueue<Response> = LinkedBlockingQueue()
    private val lock = ReentrantLock()
    private val writeCondition = lock.newCondition()

    private val eventListener : ConnectionEventListener = ConnectionEventListener().apply {
        onRead = {
            device, payload ->
            if (device == bluetoothDevice && payload.size >= MIN_RESPONSE_SIZE) {
                responseQueue.put(Response(payload))
            }
        }
        onWrite = {
            device ->
            if (device == bluetoothDevice) {
                writeCondition.signal()
            }
        }

    }
    val channels : List<Channel>

    init {

        ConnectionManager.registerListener(eventListener)
        write(CommandPacket(CMD_IDENTIFY).toByteArray())
        val nameResponse = getResponse().checkStatus()
        name = String(nameResponse.data, StandardCharsets.UTF_8)

        write(CommandPacket(CMD_GET_STATE).toByteArray())
        val stateResponse = getResponse().checkStatus().checkData()
        voltage = calculateVoltage(stateResponse.data[0])
        waterLevel = calculateWaterLevel(stateResponse.data[1])

        val readChannelsCount = CommandPacket(CMD_READ_CONFIG).addByte(1).toByteArray()
        write(readChannelsCount)
        val countResponse = getResponse().checkStatus().checkData()
        val channelsCount = countResponse.data[0].toInt()
        if (channelsCount == 0) channels = emptyList()
        else {
            val readChannels = CommandPacket(CMD_READ_CONFIG).addByte(1 + channelsCount * 8).toByteArray()
            write(readChannels)
            val channelsResponse = getResponse().checkStatus().checkData()
            val channelStream = ByteArrayInputStream(channelsResponse.data)
            channelStream.read() //skip channels count
            channels = List(channelsCount) {
                Channel(channelStream)
            }
        }
    }

    private fun calculateWaterLevel(raw : Byte) : Int {
        return when {
            raw < 17 -> 40
            raw < 55 -> 30
            raw < 92 -> 20
            raw < 132 -> 10
            else -> 0
        }
    }

    private fun calculateVoltage(raw : Byte) : Float {
        return raw * 0.02664f
    }

    private fun write(cmd : ByteArray) {
        ConnectionManager.write(bluetoothDevice, cmd)
        if (!writeCondition.await(1000, TimeUnit.MILLISECONDS)) throw ConnectionException()
    }

    private fun getResponse(timeout: Long) : Response {
        return responseQueue.poll(timeout, TimeUnit.MILLISECONDS) ?: throw TimeoutException()
    }

    private fun getResponse() : Response {
        return getResponse(DEFAULT_TIMEOUT)
    }

    fun loadConfig() {

    }
}

fun getWeek(stream : ByteArrayInputStream) : Array<Boolean> {
    val weekByte = stream.read()
    return Array(7) {
                i -> weekByte and (1 shl i) != 0
            }
}

fun getTime(stream : ByteArrayInputStream) : Calendar {
    val hours = stream.read()
    val minutes = stream.read()
    val seconds = stream.read()
    val date = Calendar.getInstance()
    date.set(Calendar.HOUR, hours)
    date.set(Calendar.MINUTE, minutes)
    date.set(Calendar.SECOND, seconds)
    return date
}