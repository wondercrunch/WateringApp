package com.ilyapiskunov.wateringapp

import java.io.ByteArrayInputStream
import java.util.*


class Channel (val week1 : Array<Boolean>,
               val week2 : Array<Boolean>,
               var timeOn : Calendar,
               var timeOff : Calendar) {

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