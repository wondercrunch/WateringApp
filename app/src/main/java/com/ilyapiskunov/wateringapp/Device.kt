package com.ilyapiskunov.wateringapp

import android.bluetooth.BluetoothDevice

const val CMD_GET_CONFIG = 0x86

class Device(val bluetoothDevice : BluetoothDevice) {
    val name : String

    init {
        name = "bla"
    }
}