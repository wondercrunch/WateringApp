package com.ilyapiskunov.wateringapp

import java.io.ByteArrayInputStream
import java.util.*


class Channel (rawData: ByteArrayInputStream) {

    val week1 : Array<Boolean>
    val week2 : Array<Boolean>
    val timeOn : Calendar
    val timeOff : Calendar

    init {
        val initWeek: (Int) -> Array<Boolean> = {
                byte -> Array(7) {
                            i -> byte and (1 shl i) != 0
                        }
        }

        week1 = initWeek(rawData.read())
        week2 = initWeek(rawData.read())

        val initTime: (ByteArrayInputStream) -> Calendar = {
                stream ->
            val hours = stream.read()
            val minutes = stream.read()
            val seconds = stream.read()
            val date = Calendar.getInstance()
            date.set(Calendar.HOUR, hours)
            date.set(Calendar.MINUTE, minutes)
            date.set(Calendar.SECOND, seconds)
            date
        }

        timeOn = initTime(rawData)
        timeOff = initTime(rawData)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Channel

        if (!week1.contentEquals(other.week1)) return false
        if (!week2.contentEquals(other.week2)) return false
        if (timeOn != other.timeOn) return false
        if (timeOff != other.timeOff) return false

        return true
    }

    override fun hashCode(): Int {
        var result = week1.contentHashCode()
        result = 31 * result + week2.contentHashCode()
        result = 31 * result + timeOn.hashCode()
        result = 31 * result + timeOff.hashCode()
        return result
    }


}