package com.ilyapiskunov.wateringapp.model

class DeviceEventListener {
    lateinit var onCommandStart: ((WateringDevice, Command) -> Unit)
    lateinit var onCommandSuccess: ((WateringDevice, Command) -> Unit)
    lateinit var onCommandError: ((WateringDevice, Command, Exception) -> Unit)
}