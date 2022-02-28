package com.ilyapiskunov.wateringapp.connection

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt

class ConnectionEventListener {
    var onConnectionSetupComplete: ((BluetoothDevice) -> Unit)? = null
    var onDisconnect: ((BluetoothDevice) -> Unit)? = null
    var onRead: ((BluetoothDevice, ByteArray) -> Unit)? = null
    var onWrite: ((BluetoothDevice, ByteArray) -> Unit)? = null
}