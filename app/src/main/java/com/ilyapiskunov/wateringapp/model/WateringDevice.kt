package com.ilyapiskunov.wateringapp.model

import android.bluetooth.BluetoothDevice
import android.util.Log
import com.ilyapiskunov.wateringapp.ble.connection.ConnectionEventListener
import com.ilyapiskunov.wateringapp.ble.connection.ConnectionManager
import com.ilyapiskunov.wateringapp.exception.TimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

const val CMD_READ_CONFIG = 0x86
const val CMD_LOAD_CONFIG = 0x84
const val CMD_IDENTIFY = 0x8A
const val CMD_SET_NAME = 0x8B
const val CMD_GET_STATE = 0x6E
const val CMD_RF_ON = 0x68
const val CMD_RF_OFF = 0x6A
const val CMD_SET_TIME = 0x70

const val ERROR_CRC = 0x03

//
const val DEFAULT_TIMEOUT = 1000L
class WateringDevice(val bluetoothDevice : BluetoothDevice, private val deviceListener : DeviceEventListener) {
    val waterLevel : Int
    val voltage : Float
    var name : String
    val mcuVersion : ByteArray
    val mcuId : ByteArray


    private val commandLock = Mutex() //for sequential execution
    private val responseQueue : BlockingQueue<Response> = LinkedBlockingQueue()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val responseReader : ResponseReader = ResponseReaderImpl()
    private val eventListener by lazy {
        ConnectionEventListener().apply {
            onRead = { device, payload ->
                if (device == bluetoothDevice) {
                    try {
                        responseReader.read(payload)?.let { response -> responseQueue.put(response) }
                    } catch (e: Exception) {
                        responseReader.reset()
                        e.printStackTrace()
                    }
                }
            }
            onWrite = { device, value ->
                if (device == bluetoothDevice) {
                    //TODO add onWrite handling
                }
            }

        }
    }

    val channels : List<Channel>

        init {

            ConnectionManager.registerListener(eventListener)
            write(CommandPacket(CMD_IDENTIFY))
            val mcuInfoResponse = getResponse().checkStatus().checkData()

            val nameSize = PacketFormat.DEVICE_NAME_MAX_BYTE_SIZE
            name = String(mcuInfoResponse.data, 0, nameSize, PacketFormat.getCharset()).trim()
            mcuVersion = mcuInfoResponse.data.copyOfRange(nameSize, nameSize+2)
            mcuId = mcuInfoResponse.data.copyOfRange(nameSize+2, nameSize+4)

            write(CommandPacket(CMD_GET_STATE))
            val stateResponse = getResponse().checkStatus().checkData()
            voltage = calculateVoltage(stateResponse.data[0])
            waterLevel = calculateWaterLevel(stateResponse.data[1])

            val readChannelsCount = CommandPacket(CMD_READ_CONFIG).put(1)
            write(readChannelsCount)
            val countResponse = getResponse().checkStatus().checkData()
            val channelsCount = countResponse.data[0].toInt()
            if (channelsCount == 0) channels = emptyList()
            else {

                val readChannels = CommandPacket(CMD_READ_CONFIG).put(1 + channelsCount * 8)
                write(readChannels)
                val channelsResponse = getResponse().checkStatus().checkData()
                val channelStream = ByteArrayInputStream(channelsResponse.data)
                channelStream.read() //skip channels count
                channels = List(channelsCount) {

                    val week1 = readWeek(channelStream)
                    val week2 = readWeek(channelStream)

                    val timeOn = readTime(channelStream)
                    val timeOff = readTime(channelStream)
                    Channel(week1, week2, timeOn, timeOff)
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
            return raw.toUByte().toFloat() * 0.02664f
        }

        private fun write(packet : CommandPacket) {
            ConnectionManager.write(bluetoothDevice, packet.toByteArray())
        }

        private fun getResponse(timeout: Long) : Response {
            val response = responseQueue.poll(timeout, TimeUnit.MILLISECONDS) ?: throw TimeoutException()
            Log.i("SprinklerDevice", "Got response: $response")
            return response
        }

        private fun getResponse() : Response {
            return getResponse(DEFAULT_TIMEOUT)
        }

        private fun writeAndGetValidResponse(packet : CommandPacket) : Response {
            write(packet)
            return getResponse().checkStatus()
        }

        private fun runCommand(name: String, command: suspend (() -> Unit)) {
            scope.launch(Dispatchers.IO) {
                commandLock.withLock {
                    try {
                        Log.i("SprinklerDevice", "Running command: $name")
                        deviceListener.onCommandStart.invoke(this@WateringDevice, name)
                        command.invoke()
                        deviceListener.onCommandSuccess.invoke(this@WateringDevice, name)
                    } catch (e : Exception) {
                        deviceListener.onCommandError.invoke(this@WateringDevice, name, e)
                    }
                }
            }
        }

        fun loadConfig(callback: (() -> Unit)? = null) {
            runCommand("Load Config") {
                val packet = CommandPacket(CMD_LOAD_CONFIG)
                channels.forEach { channel -> packet.put(channel.toByteArray())}
                writeAndGetValidResponse(packet)
                callback?.invoke()
            }
        }

        fun setTime(time: Calendar, callback: (() -> Unit)? = null) {
            runCommand("Set Time") {
                val packet = CommandPacket(CMD_SET_TIME)
                val day = (time.get(Calendar.DAY_OF_WEEK) - 2).mod(7) //0..6
                var dayByte = 1 shl day
                if (time.get(Calendar.WEEK_OF_YEAR) % 2 != 0)
                    dayByte = dayByte or 0x80
                packet.put(dayByte)
                    .put(time.get(Calendar.HOUR_OF_DAY))
                    .put(time.get(Calendar.MINUTE))
                    .put(time.get(Calendar.SECOND))
                writeAndGetValidResponse(packet)
                callback?.invoke()
            }
        }

        fun toggleChannel(on : Boolean, channel: Int, callback: (() -> Unit)? = null) {
            runCommand("Toggle Channel") {
                val packet = CommandPacket(if (on) CMD_RF_ON else CMD_RF_OFF)
                packet.put(channel)
                writeAndGetValidResponse(packet)
                callback?.invoke()
            }
        }

        fun setDeviceName(name : String, callback: (() -> Unit)? = null) {
            runCommand("Set Name") {
                val packet = CommandPacket(CMD_SET_NAME)
                val encodedName = name.toByteArray(PacketFormat.getCharset())
                packet.put(encodedName)
                    .put(ByteArray(PacketFormat.DEVICE_NAME_MAX_BYTE_SIZE - encodedName.size) { 0x20 }) //добить до 20 байт пробелами
                writeAndGetValidResponse(packet)
                synchronized(this.name) {
                    this.name = name
                }
                callback?.invoke()
            }

        }

        fun disconnect() {
            ConnectionManager.disconnect(bluetoothDevice)
        }

        fun unregisterListener() {
            ConnectionManager.unregisterListener(eventListener)
        }





}


fun readWeek(stream : ByteArrayInputStream) : Array<Boolean> {
    val weekByte = stream.read()
    return Array(7) {
                i -> weekByte and (1 shl i) != 0
            }
}

fun readTime(stream : ByteArrayInputStream) : AlarmTime {
    val hours = stream.read()
    val minutes = stream.read()
    val seconds = stream.read()
    return AlarmTime(hours, minutes, seconds)
}
