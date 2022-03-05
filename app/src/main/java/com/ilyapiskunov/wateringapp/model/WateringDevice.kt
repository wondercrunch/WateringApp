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
    private val responseReader = ResponseReader(responseQueue)
    private val eventListener by lazy {
        ConnectionEventListener().apply {
            onRead = { device, payload ->
                if (device == bluetoothDevice) {
                    try {
                        responseReader.handle(payload)
                    } catch (e: Exception) {
                        responseReader.reset()
                        e.printStackTrace()
                    }
                }
            }
            onWrite = { device, value ->
                if (device == bluetoothDevice) {

                }
            }

        }
    }
    val channels : List<Channel>

        init {

            ConnectionManager.registerListener(eventListener)
            write(CommandPacket(CMD_IDENTIFY))
            val mcuInfoResponse = getResponse().checkStatus()
            name = String(mcuInfoResponse.data, 0, 10, StandardCharsets.UTF_8)
            mcuVersion = mcuInfoResponse.data.copyOfRange(10, 12)
            mcuId = mcuInfoResponse.data.copyOfRange(12, 14)

            write(CommandPacket(CMD_GET_STATE))
            val stateResponse = getResponse().checkStatus().checkData()
            voltage = calculateVoltage(stateResponse.data[0])
            waterLevel = calculateWaterLevel(stateResponse.data[1])

            val readChannelsCount = CommandPacket(CMD_READ_CONFIG).add(1)
            write(readChannelsCount)
            val countResponse = getResponse().checkStatus().checkData()
            val channelsCount = countResponse.data[0].toInt()
            if (channelsCount == 0) channels = emptyList()
            else {

                val readChannels = CommandPacket(CMD_READ_CONFIG).add(1 + channelsCount * 8)
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

        private fun runCommand(command: Command, callback: (() -> Unit)? = null) {
            scope.launch(Dispatchers.IO) {
                commandLock.withLock {
                    try {
                        Log.i("SprinklerDevice", "Running command: $command")
                        deviceListener.onCommandStart.invoke(this@WateringDevice, command)
                        when (command) {
                            is LoadConfig -> with(command) {
                                channels.forEach { channel -> packet.add(channel.toByteArray())}
                            }

                            is SetTime -> with(command) {
                                val day = (time.get(Calendar.DAY_OF_WEEK) - 2).mod(7) //0..6
                                var dayByte = 1 shl day
                                if (time.get(Calendar.WEEK_OF_YEAR) % 2 != 0)
                                    dayByte = dayByte or 0x80
                                packet.add(dayByte)
                                    .add(time.get(Calendar.HOUR_OF_DAY))
                                    .add(time.get(Calendar.MINUTE))
                                    .add(time.get(Calendar.SECOND))
                                writeAndGetValidResponse(packet)
                            }

                            is ToggleChannel -> with(command) {
                                packet.add(channel)
                                writeAndGetValidResponse(packet)
                            }

                            is SetName -> with(command) {
                                packet.add(name.toByteArray(Charset.forName("KOI8-R")))
                                writeAndGetValidResponse(packet)
                                this@WateringDevice.name = name
                            }
                            else ->
                                return@launch
                        }
                        callback?.invoke()
                        deviceListener.onCommandSuccess.invoke(this@WateringDevice, command)
                    } catch (e : Exception) {
                        deviceListener.onCommandError.invoke(this@WateringDevice, command, e)
                    }
                }
            }
        }

        fun loadConfig(callback: (() -> Unit)? = null) {
            runCommand(LoadConfig(), callback)
        }

        fun setTime(time: Calendar, callback: (() -> Unit)? = null) {
            runCommand(SetTime(time), callback)
        }

        fun toggleChannel(on : Boolean, channel: Int, callback: (() -> Unit)? = null) {
            runCommand(ToggleChannel(channel, on), callback)
        }

        fun setDeviceName(name : String, callback: (() -> Unit)? = null) {
            runCommand(SetName(name), callback)
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
