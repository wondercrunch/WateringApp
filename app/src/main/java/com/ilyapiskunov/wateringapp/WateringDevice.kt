package com.ilyapiskunov.wateringapp

import android.bluetooth.BluetoothDevice
import android.util.Log
import com.ilyapiskunov.wateringapp.connection.ConnectionEventListener
import com.ilyapiskunov.wateringapp.connection.ConnectionManager
import com.ilyapiskunov.wateringapp.exception.TimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.*

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

const val ERROR_CRC = 0x03

const val CMD_READ_CONFIG = 0x86
const val CMD_LOAD_CONFIG = 0x84
const val CMD_IDENTIFY = 0x8A
const val CMD_GET_STATE = 0x6E
const val CMD_RF_ON = 0x68
const val CMD_RF_OFF = 0x6A
const val CMD_SET_TIME = 0x70
const val DEFAULT_TIMEOUT = 1000L
class WateringDevice(val bluetoothDevice : BluetoothDevice, private val deviceListener : DeviceEventListener) {
    val waterLevel : Int
    val voltage : Float
    val name : String

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
            write(CommandPacket(CMD_IDENTIFY).toByteArray())
            val nameResponse = getResponse().checkStatus()
            name = String(nameResponse.data, 0, 10, StandardCharsets.UTF_8)

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

        private fun write(cmd : ByteArray) {
            ConnectionManager.write(bluetoothDevice, cmd)
        }

        private fun getResponse(timeout: Long) : Response {
            val response = responseQueue.poll(timeout, TimeUnit.MILLISECONDS) ?: throw TimeoutException()
            Log.i("SprinklerDevice", "Got response: $response")
            return response
        }

        private fun getResponse() : Response {
            return getResponse(DEFAULT_TIMEOUT)
        }

        private fun runCommand(name : String, command: suspend () -> Unit) {
            scope.launch(Dispatchers.IO) {
                commandLock.withLock {
                    try {
                        Log.i("SprinklerDevice", "Running command: $name")
                        deviceListener.onCommandStart.invoke(name)
                        command()
                        deviceListener.onCommandSuccess.invoke(name)
                    } catch (e : Exception) {
                        deviceListener.onCommandError.invoke(name, e)
                    }
                }
            }
        }

        fun loadConfig() {
            runCommand("Load Config") {
                val cmd = CommandPacket(CMD_LOAD_CONFIG)
                channels.forEach { channel -> cmd.addBytes(channel.toByteArray())}
                write (cmd.toByteArray())
                getResponse().checkStatus()
            }
        }

        fun setTime() {
            runCommand("Set Time") {
                val cmd = CommandPacket(CMD_SET_TIME)
                val calendar = Calendar.getInstance()
                val day = (calendar.get(Calendar.DAY_OF_WEEK) - 2).mod(7) //0..6
                var dayByte = 1 shl day
                if (calendar.get(Calendar.WEEK_OF_YEAR) % 2 != 0)
                    dayByte = dayByte or 0x80
                cmd.addByte(dayByte)
                    .addByte(calendar.get(Calendar.HOUR_OF_DAY))
                    .addByte(calendar.get(Calendar.MINUTE))
                    .addByte(calendar.get(Calendar.SECOND))
                write(cmd.toByteArray())
                getResponse().checkStatus()
            }
        }

        fun toggleChannel(on : Boolean, channel: Int) {
            runCommand("Toggle Channel") {
                val cmd = CommandPacket(if (on) CMD_RF_ON else CMD_RF_OFF).addByte(channel).toByteArray()
                write(cmd)
                getResponse().checkStatus()
            }
        }

        fun disconnect() {
            ConnectionManager.disconnect(bluetoothDevice)
        }

        fun unregisterListener() {
            ConnectionManager.unregisterListener(eventListener)
        }

        private class ResponseReader(val responseQueue: BlockingQueue<Response>) {
            private enum class State {
                WAITING,
                PREFIX_RECEIVED,
                DATA_RECEIVED
            }
            private val PREFIX_LENGTH = 3
            private val DATA_LENGTH_INDEX = 2
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
                                dataLength = buffer[DATA_LENGTH_INDEX].toInt()
                                buffer = buffer.copyOf(PREFIX_LENGTH + dataLength + 1 /* CRC8 byte */)
                                state = State.PREFIX_RECEIVED

                            }
                            State.PREFIX_RECEIVED -> {
                                val crc = buffer[buffer.size - 1]
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

            fun reset() {
                state = State.WAITING
                insertIndex = 0
                dataLength = 0
                buffer = ByteArray(PREFIX_LENGTH)
            }


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