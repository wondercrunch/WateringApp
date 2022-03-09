package com.ilyapiskunov.wateringapp.model

interface ResponseReader {
    fun read(payload : ByteArray) : Response?
    fun reset()
}