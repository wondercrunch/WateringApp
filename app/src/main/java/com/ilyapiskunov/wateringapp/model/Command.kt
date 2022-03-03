package com.ilyapiskunov.wateringapp.model

import java.util.*

const val CMD_READ_CONFIG = 0x86
const val CMD_LOAD_CONFIG = 0x84
const val CMD_IDENTIFY = 0x8A
const val CMD_SET_NAME = 0x8B
const val CMD_GET_STATE = 0x6E
const val CMD_RF_ON = 0x68
const val CMD_RF_OFF = 0x6A
const val CMD_SET_TIME = 0x70

sealed class Command(code : Int) {
    val packet = CommandPacket(code)

    override fun toString(): String {
        return javaClass.simpleName
    }
}

class LoadConfig : Command(CMD_LOAD_CONFIG)
class ReadConfig(val byteCount : Int) : Command(CMD_READ_CONFIG)
class Identify : Command(CMD_IDENTIFY)
class GetState : Command(CMD_GET_STATE)
class ToggleChannel(val channel : Int, on : Boolean) : Command(if (on) CMD_RF_ON else CMD_RF_OFF)
class SetTime(val time: Calendar) : Command(CMD_SET_TIME)
class SetName(val name : String) : Command(CMD_SET_NAME)