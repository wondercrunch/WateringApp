package com.ilyapiskunov.wateringapp.model

import android.bluetooth.BluetoothDevice
import android.util.Log
import com.ilyapiskunov.wateringapp.Tools
import com.ilyapiskunov.wateringapp.Tools.toHex
import com.ilyapiskunov.wateringapp.ble.connection.ConnectionEventListener
import com.ilyapiskunov.wateringapp.ble.connection.ConnectionManager
import com.ilyapiskunov.wateringapp.exception.TimeoutException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit


class WateringDevice(val bluetoothDevice : BluetoothDevice, private val deviceListener : DeviceEventListener) {
    var waterLevel : Int = 0
        private set
    var voltage : Double = 0.0
        private set
    var name : String = bluetoothDevice.address
        private set
    val mcuVersion : ByteArray
    val mcuId : Int


    enum class Command(val code : Int) {
        READ_CONFIG(0x86),
        LOAD_CONFIG(0x84),
        IDENTIFY(0x8A),
        SET_NAME(0x8B),
        GET_STATE(0x6E),
        OPEN(0x68),
        CLOSE(0x6A),
        SET_TIME(0x70),
        CONNECTION_PROBE(0x86)
    }

    private val commandLock = Mutex() //for sequential execution
    private val responseQueue : BlockingQueue<Response> = LinkedBlockingQueue()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val responseReader : ResponseReader = ResponseReaderImpl()
    private val eventListener by lazy {
        ConnectionEventListener().apply {
            onRead = { device, payload ->
                if (device == bluetoothDevice) {
                    Log.i(name, "RX: " + payload.toHex(" "))
                    //deviceListener.onRead.invoke(this@WateringDevice, payload)
                    try {
                        responseReader.read(payload)?.let { response -> responseQueue.put(response) }
                    } catch (e: Exception) {
                        responseReader.reset()
                        e.printStackTrace()
                    }
                }
            }
            onWrite = { device, payload ->
                if (device == bluetoothDevice) {
                    Log.i(name, "TX: " + payload.toHex(" "))
                    //deviceListener.onWrite.invoke(this@WateringDevice, payload)
                }
            }

        }
    }

    /**
     * Sends command every 2 sec for device mcu to keep up the ble connection
     */
    private inner class ConnectionProbe() : TimerTask() {
        override fun run() {
            runCommand(Command.CONNECTION_PROBE) {
                val payload = CommandPacket(Command.CONNECTION_PROBE.code).put(1).toByteArray()
                ConnectionManager.write(bluetoothDevice, payload)
                val response = responseQueue.poll(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS) ?: throw TimeoutException()
                response.checkStatus()
                Log.i(name, "Connection probe OK")
            }
        }
    }

    private val connectionProbeTimer = Timer()

    val channels : List<Channel>

    /**
     * Init block reads device name, mcu info and channels
     */
    init {
        ConnectionManager.registerListener(eventListener)
        deviceListener.onCommandStart.invoke(this@WateringDevice, Command.IDENTIFY)
        CommandPacket(Command.IDENTIFY.code)
            .send()
        val mcuInfoResponse = getResponse().checkStatus().checkData()

        val nameSize = PacketFormat.DEVICE_NAME_MAX_BYTE_SIZE
        name = String(mcuInfoResponse.data, 0, nameSize, PacketFormat.getCharset()).trim()

        //2 byte array
        mcuVersion = mcuInfoResponse.data.copyOfRange(nameSize, nameSize+2)

        //4 byte integer
        mcuId = ByteBuffer.wrap(mcuInfoResponse.data, nameSize+2, 4).order(ByteOrder.BIG_ENDIAN).getInt()
        deviceListener.onCommandSuccess.invoke(this@WateringDevice, Command.IDENTIFY)
        //get channels count
        deviceListener.onCommandStart.invoke(this@WateringDevice, Command.READ_CONFIG)
        CommandPacket(Command.READ_CONFIG.code)
            .put(1) //read 1 byte - channels count
            .send()
        val countResponse = getResponse().checkStatus().checkData()
        deviceListener.onCommandSuccess.invoke(this@WateringDevice, Command.READ_CONFIG)
        val channelsCount = countResponse.data[0].toInt()
        if (channelsCount == 0) channels = emptyList()
        else {
            deviceListener.onCommandStart.invoke(this@WateringDevice, Command.READ_CONFIG)

            //get channels
            CommandPacket(Command.READ_CONFIG.code)
                .put(1 + channelsCount * 8) //channel size (8 bytes) * channels count 
                .send()
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
            deviceListener.onCommandSuccess.invoke(this@WateringDevice, Command.READ_CONFIG)
        }
        connectionProbeTimer.scheduleAtFixedRate(ConnectionProbe(), 2000, 2000)
    }


    private fun getResponse(timeout: Long) : Response {
        val response = responseQueue.poll(timeout, TimeUnit.MILLISECONDS) ?: throw TimeoutException()
        Log.i(name, "Got response: $response")
        deviceListener.onRead(this, response.rawBytes)
        return response
    }

    private fun getResponse() : Response = getResponse(DEFAULT_TIMEOUT)

    /**
     * Runs commands without blocking UI thread but with lock for device MCU to handle them synchronously
     */
    private fun runCommand(command : Command, block: (() -> Unit)) : Job {
        return scope.launch(Dispatchers.IO) {
            commandLock.withLock {

                try {
                    Log.i(name, "Running command: $command")
                    deviceListener.onCommandStart.invoke(this@WateringDevice, command)
                    block.invoke()
                    deviceListener.onCommandSuccess.invoke(this@WateringDevice, command)
                } catch (e: Exception) {
                    deviceListener.onCommandError.invoke(this@WateringDevice, command, e)
                }
                delay(10)

            }
        }
    }


    /**
     * Get device voltage and water level values
     */
    fun getState() : Job {
        return runCommand(Command.GET_STATE) {
            CommandPacket(Command.GET_STATE.code)
                .send()
            val stateResponse = getResponse().checkStatus().checkData()
            voltage = calculateVoltage(stateResponse.data[0])
            waterLevel = calculateWaterLevelPercentage(stateResponse.data[1])
        }

    }

    /**
     * Save current channels config
     */
    fun loadConfig() : Job {
        return runCommand(Command.LOAD_CONFIG) {
            val packet = CommandPacket(Command.LOAD_CONFIG.code)
            channels.forEach { channel -> packet.put(channel.toByteArray()) }
            packet.send()
            getResponse().checkStatus()
        }
    }

    /**
     * Writes current day of week, week parity and time
     */
    fun setTime(time: Calendar) : Job {
        return runCommand(Command.SET_TIME) {
            val day = (time.get(Calendar.DAY_OF_WEEK) - 2).mod(7) //day must be 0..6
            var dayByte = 1 shl day
            if (!Tools.isCurrentWeekEven()) //MSB is week parity
                dayByte = dayByte or 0x80

            CommandPacket(Command.SET_TIME.code)
                .put(dayByte)
                .put(time.get(Calendar.HOUR_OF_DAY))
                .put(time.get(Calendar.MINUTE))
                .put(time.get(Calendar.SECOND))
                .send()
            getResponse().checkStatus()
        }
    }

    /**
     * Toggles channel state (OPEN-CLOSE)
     */
    fun toggleChannel(open : Boolean, channel: Int) : Job {
        val cmd = if (open) Command.OPEN else Command.CLOSE
        return runCommand(cmd) {
            CommandPacket(cmd.code)
                .put(channel)
                .send()
            getResponse().checkStatus()
        }
    }

    /**
     * Writes device name
     */
    fun setName(name : String) : Job {
        return runCommand(Command.SET_NAME) {
            val encodedName = name.toByteArray(PacketFormat.getCharset())
            if (encodedName.size > PacketFormat.DEVICE_NAME_MAX_BYTE_SIZE)
                throw Exception("?????????? ?????????? ???? ???????????? ?????????????????? ${PacketFormat.DEVICE_NAME_MAX_BYTE_SIZE} ????????")


            CommandPacket(Command.SET_NAME.code)
                .put(encodedName)
                .put(ByteArray(PacketFormat.DEVICE_NAME_MAX_BYTE_SIZE - encodedName.size) { 0x20 }) //Fill remaining bytes with spaces
                .send()
            getResponse().checkStatus()
            this.name = name
        }

    }

    fun shutdown() {
        connectionProbeTimer.cancel()
        channels.forEach { channel -> channel.stopTimer() }
        unregisterListener()
    }

    fun disconnect() {
        shutdown()
        ConnectionManager.disconnect(bluetoothDevice)
    }

    fun unregisterListener() {
        ConnectionManager.unregisterListener(eventListener)
    }

    inner class CommandPacket(code : Int) {
        private val cmdStream : ByteArrayOutputStream = ByteArrayOutputStream()
        private val dataStream : ByteArrayOutputStream = ByteArrayOutputStream()

        init {
            cmdStream.write(PacketFormat.TX_BUS_ADDRESS)
            cmdStream.write(code)
        }

        fun put(byte : Int): CommandPacket {
            dataStream.write(byte)
            return this
        }

        fun put(bytes: ByteArray): CommandPacket {
            dataStream.write(bytes)
            return this
        }


        //MSB first
        private fun ByteArrayOutputStream.putValue(value : Int, byteSize : Int) {
            for (i in byteSize-1 downTo 0)
                this.write((value ushr 8*i) and 0xFF)
        }

        fun put(value : Int, byteSize : Int) {
            dataStream.putValue(value, byteSize)
        }

        fun send() {
            val payload = toByteArray()
            ConnectionManager.write(bluetoothDevice, payload)
            deviceListener.onWrite(this@WateringDevice, payload)

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

    companion object WateringDeviceUtils {

        const val DEFAULT_TIMEOUT = 1000L

        private fun readWeek(stream : ByteArrayInputStream) : Array<Boolean> {
            val weekByte = stream.read()
            return Array(7) {
                    i -> weekByte and (1 shl i) != 0
            }
        }

        private fun readTime(stream : ByteArrayInputStream) : ChannelControlTimer {
            val hours = stream.read()
            val minutes = stream.read()
            val seconds = stream.read()
            return ChannelControlTimer(hours, minutes, seconds)
        }

        private fun calculateWaterLevelPercentage(raw : Byte) : Int {
            return when {
                raw < 17 -> 40
                raw < 55 -> 30
                raw < 92 -> 20
                raw < 132 -> 10
                else -> 0
            }
        }

        private fun calculateVoltage(raw : Byte) : Double {
            return raw.toUByte().toDouble() * 0.02664
        }
    }

}


