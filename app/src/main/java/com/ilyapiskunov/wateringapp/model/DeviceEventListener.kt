package com.ilyapiskunov.wateringapp.model

import android.bluetooth.BluetoothDevice

class DeviceEventListener {
    lateinit var onCommandStart: ((WateringDevice, WateringDevice.Command) -> Unit)
    lateinit var onCommandSuccess: ((WateringDevice, WateringDevice.Command) -> Unit)
    lateinit var onCommandError: ((WateringDevice, WateringDevice.Command, Exception) -> Unit)
    lateinit var onRead: ((WateringDevice, ByteArray) -> Unit)
    lateinit var onWrite: ((WateringDevice, ByteArray) -> Unit)
}