package com.ilyapiskunov.wateringapp.ble.connection

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ilyapiskunov.wateringapp.Tools.toHex
import com.ilyapiskunov.wateringapp.exception.ConnectionException
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

object ConnectionManager {

    private val TAG = "ConnectionManager"
    private var listeners : MutableSet<WeakReference<ConnectionEventListener>> = mutableSetOf()
    var isConnected = false
    private val operationQueue = ConcurrentLinkedQueue<Operation>()
    private var pendingOperation : Operation? = null
    private val deviceGattMap = ConcurrentHashMap<BluetoothDevice, BluetoothGatt> ()
    private const val SERIAL_CHARACTERISTIC_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb"
    private const val SERIAL_SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb"
    private val serialServiceUUID = UUID.fromString(SERIAL_SERVICE_UUID)
    private val serialCharacteristicUUID = UUID.fromString(SERIAL_CHARACTERISTIC_UUID)

    fun registerListener(listener: ConnectionEventListener) {
        if (listeners.map { it.get() }.contains(listener)) return
        listeners.add(WeakReference(listener))
    }

    fun unregisterListener(listener: ConnectionEventListener) {
        var refToRemove : WeakReference<ConnectionEventListener>? = null
        listeners.forEach{ ref -> if (ref.get() == listener) {
            refToRemove = ref
        } }
        listeners.remove(refToRemove)
    }

    fun connect(device : BluetoothDevice, context : Context) {
        if (deviceGattMap[device] == null)
            enqueueOperation(Connect(device, context))
    }

    fun disconnect(device: BluetoothDevice) {
        if (deviceGattMap[device] != null)
            enqueueOperation(Disconnect(device))
    }


    fun write(device: BluetoothDevice, data : ByteArray) {
        if (deviceGattMap[device] != null) {
            enqueueOperation(Write(device, data))
        }
        else {
            deviceGattMap.forEach { entry -> Log.i("ConnectionManager", "Device: ${entry.key.address}") }
            Log.i("ConnectionManager", "No ${device.address} in map!")
            throw ConnectionException()
        }
    }

    @Synchronized
    private fun enqueueOperation(operation: Operation) {
        //Log.i("ConnectionManager", "Enqueueing operation: $operation")
        operationQueue.add(operation)
        if (pendingOperation == null) {
            nextOperation()
        }
    }

    @Synchronized
    private fun signalEndOfOperation() {
        pendingOperation = null
        if (operationQueue.isNotEmpty()) {
            nextOperation()
        }
    }


    @Synchronized
    private fun nextOperation() {
        if (pendingOperation != null) return

        val operation = operationQueue.poll() ?: return
        pendingOperation = operation

        if (operation is Connect) {
            Log.i("ConnectionManager", "Executing operation Connect")
            with(operation) {
                device.connectGatt(context, false, callback)
            }
            return
        }

        val gatt = deviceGattMap[operation.device] ?: run {
            signalEndOfOperation()
            return
        }
        Log.i("ConnectionManager", "Executing operation: $operation")
        when (operation) {

            is Disconnect -> with(operation) {
                Log.i(TAG, "Disconnecting from ${device.name}")
                gatt.close()
                deviceGattMap.remove(device)
                listeners.forEach { ref -> ref.get()?.onDisconnect?.invoke(device) }
                signalEndOfOperation()
            }

            is Write -> with(operation) {
                val serialCharacteristic = gatt
                    .getService(serialServiceUUID)
                    .getCharacteristic(serialCharacteristicUUID)
                serialCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                serialCharacteristic.value = data
                val res = gatt.writeCharacteristic(serialCharacteristic)
                Log.i(TAG, "write data init success: $res")
                listeners.forEach { ref -> ref.get()?.onWrite?.invoke(gatt.device, data) }
                signalEndOfOperation()
            }
        }

    }

    private val callback : BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Log.i("BluetoothGattCallback", "Connected to $deviceAddress");
                    deviceGattMap[gatt.device] = gatt
                    Handler(Looper.getMainLooper()).post {
                        gatt.discoverServices()
                    }


                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.i("BluetoothGattCallback", "Disconnected from $deviceAddress")
                    disconnect(gatt.device)
                    //bluetoothGatt = null
                }
            }
            else {
                Log.i("BluetoothGattCallback", "Connection error with $deviceAddress: $status")
                if (pendingOperation is Connect) {
                    signalEndOfOperation()
                }
                disconnect(gatt.device)
                //bluetoothGatt = null
            }

        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with (gatt) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    printGattTable()


                    val serialCharacteristic =
                        getService(serialServiceUUID).getCharacteristic(serialCharacteristicUUID)
                    //gatt.readCharacteristic(gatt.getService(appServiceUUID).getCharacteristic(appCharUUID))
                    val notificationEnabled = setCharacteristicNotification(serialCharacteristic, true)
                    if (notificationEnabled) {
                        requestMtu(90)
                        return
                    }
                }

                Log.i(TAG, "Error connecting to $device")
                disconnect(device)
                if (pendingOperation is Connect)
                    signalEndOfOperation()
                //write("test".toByteArray())
            }




        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.i("BluetoothGattCallback", "MTU chaged to $mtu, success:${status == BluetoothGatt.GATT_SUCCESS}")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                listeners.forEach { ref -> ref.get()?.onConnectionSetupComplete?.invoke(gatt!!.device) }
            }
            else {
                Log.i("BluetoothGattCallback", "Error in MTU change request")
                enqueueOperation(Disconnect(gatt!!.device))
            }

            if (pendingOperation is Connect)
                signalEndOfOperation()
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with (characteristic) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.i("BluetoothGattCallback", "characteristic $uuid write success")

                }
                else {
                    Log.i("BluetoothGattCallback", "characteristic $uuid write fail: $status")
                }
            }

        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            with (characteristic) {
                Log.i("BluetoothGattCallback", "characteristic $uuid changed, new value = ${value.toHex(" ")}")
                if (uuid == serialCharacteristicUUID) {
                    //replyQueue.put(value)
                    listeners.forEach { ref -> ref.get()?.onRead?.invoke(gatt.device, value) }
                }
            }
        }


    }

    private fun BluetoothGatt.printGattTable() {
        if (services.isEmpty()) {
            Log.i("printGattTable", "No service and characteristic available, call discoverServices() first?")
            return
        }
        services.forEach { service ->
            val characteristicsTable = service.characteristics.joinToString(
                separator = "\n|--",
                prefix = "|--"
            ) { it.uuid.toString() }
            Log.i("printGattTable", "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
            )
        }
    }


}