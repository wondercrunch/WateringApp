package com.ilyapiskunov.wateringapp

class DeviceEventListener {
    lateinit var onCommandSuccess: (() -> Unit)
    lateinit var onCommandError: ((Exception) -> Unit)
}