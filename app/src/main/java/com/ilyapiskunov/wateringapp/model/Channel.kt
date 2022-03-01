package com.ilyapiskunov.wateringapp.model

import kotlin.experimental.or


class Channel (val week1 : Array<Boolean>,
               val week2 : Array<Boolean>,
               var timeOn : AlarmTime,
               var timeOff : AlarmTime
) {

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

    fun toByteArray() :ByteArray {
        val res = ByteArray(8)
        for (i in 0..6) {
            if (week1[i]) res[0] = res[0] or (1 shl i).toByte()
            if (week2[i]) res[1] = res[1] or (1 shl i).toByte()
        }
        res[2] = timeOn.hours.toByte()
        res[3] = timeOn.minutes.toByte()
        res[4] = timeOn.seconds.toByte()
        res[5] = timeOff.hours.toByte()
        res[6] = timeOff.minutes.toByte()
        res[7] = timeOff.seconds.toByte()
        return res
    }

}

