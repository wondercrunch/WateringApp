package com.ilyapiskunov.wateringapp.exception

import java.util.*

const val ERROR_CRC = 0x03
const val ERROR_TIMEOUT = 0xf1
const val ERROR_DATA_CORRUPTED = 0x42
const val ERROR_UNKNOWN_CMD = 0x80

private val errorCodeMap = mapOf(
    ERROR_TIMEOUT to "Таймаут ответа",
    ERROR_DATA_CORRUPTED to "Ошибка при обработке данных",
    ERROR_CRC to "Ошибка CRC",
    ERROR_UNKNOWN_CMD to "Неизвестная команда"
)

class DeviceException(errorCode : Int) : Exception(
     errorCodeMap[errorCode] ?: "Неизвестная ошибка: ${errorCode.toString(16)}"
)
