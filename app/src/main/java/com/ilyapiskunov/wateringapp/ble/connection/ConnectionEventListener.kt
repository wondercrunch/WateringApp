package com.ilyapiskunov.wateringapp.ble.connection

import android.bluetooth.BluetoothDevice

class ConnectionEventListener {
    var onConnectionSetupComplete: ((BluetoothDevice) -> Unit)? = null
    var onDisconnect: ((BluetoothDevice) -> Unit)? = null
    var onRead: ((BluetoothDevice, ByteArray) -> Unit)? = null
    var onWrite: ((BluetoothDevice, ByteArray) -> Unit)? = null
}