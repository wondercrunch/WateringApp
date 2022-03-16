package com.ilyapiskunov.wateringapp.model

import kotlin.experimental.or


class Channel (val week1 : Array<Boolean>,
               val week2 : Array<Boolean>,
               var timerOn : AlarmTimer,
               var timerOff : AlarmTimer
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Channel

        if (!week1.contentEquals(other.week1)) return false
        if (!week2.contentEquals(other.week2)) return false
        if (timerOn != other.timerOn) return false
        if (timerOff != other.timerOff) return false

        return true
    }

    override fun hashCode(): Int {
        var result = week1.contentHashCode()
        result = 31 * result + week2.contentHashCode()
        result = 31 * result + timerOn.hashCode()
        result = 31 * result + timerOff.hashCode()
        return result
    }

    fun toByteArray() :ByteArray {
        val res = ByteArray(8)
        for (i in 0..6) {
            if (week1[i]) res[0] = res[0] or (1 shl i).toByte()
            if (week2[i]) res[1] = res[1] or (1 shl i).toByte()
        }
        res[2] = timerOn.hours.toByte()
        res[3] = timerOn.minutes.toByte()
        res[4] = timerOn.seconds.toByte()
        res[5] = timerOff.hours.toByte()
        res[6] = timerOff.minutes.toByte()
        res[7] = timerOff.seconds.toByte()
        return res
    }

}

