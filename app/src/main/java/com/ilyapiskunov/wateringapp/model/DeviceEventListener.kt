package com.ilyapiskunov.wateringapp.model

class DeviceEventListener {
    lateinit var onCommandStart: ((WateringDevice, String) -> Unit)
    lateinit var onCommandSuccess: ((WateringDevice, String) -> Unit)
    lateinit var onCommandError: ((WateringDevice, String, Exception) -> Unit)
}