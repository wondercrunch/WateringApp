package com.ilyapiskunov.wateringapp.model

class DeviceEventListener {
    lateinit var onCommandStart: ((WateringDevice, WateringDevice.Command) -> Unit)
    lateinit var onCommandSuccess: ((WateringDevice, WateringDevice.Command) -> Unit)
    lateinit var onCommandError: ((WateringDevice, WateringDevice.Command, Exception) -> Unit)
}