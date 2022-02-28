package com.ilyapiskunov.wateringapp

class DeviceEventListener {
    lateinit var onCommandStart: ((String) -> Unit)
    lateinit var onCommandSuccess: ((String) -> Unit)
    lateinit var onCommandError: ((String, Exception) -> Unit)
}