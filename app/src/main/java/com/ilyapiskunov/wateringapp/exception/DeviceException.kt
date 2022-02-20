package com.ilyapiskunov.wateringapp.exception

import java.util.*

private val errorCodeMap = mapOf(
    0xf1 to "Таймаут ответа",
    0x42 to "Ошибка при обработке данных",
    0x03 to "Ошибка CRC"
)

class DeviceException(errorCode : Int) : Exception(
     errorCodeMap[errorCode] ?: "Неизвестная ошибка: ${errorCode.toString(16)}"
)
