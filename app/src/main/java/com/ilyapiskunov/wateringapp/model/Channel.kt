package com.ilyapiskunov.wateringapp.model

import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.Exception
import java.util.*
import kotlin.experimental.or
import kotlin.math.roundToInt


class Channel (val week1 : Array<Boolean>,
               val week2 : Array<Boolean>,
               var timerOn : ChannelControlTimer,
               var timerOff : ChannelControlTimer
) {
    var timerView : TextView? = null
    private var timer : Timer? = null

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

    /**
     * Starts timer to count time passed since channel was opened
     */
    private inner class TimeTask() : TimerTask() {

        private var time = 0.0

        override fun run() {
            time++
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    timerView?.text = getTimeStringFromDouble(time)
                } catch (e : Exception) {
                    e.printStackTrace()
                }
            }

        }

        private fun getTimeStringFromDouble(time: Double) : String {
            val timeInt = time.roundToInt()
            val minutes = timeInt / 60
            val seconds = timeInt % 60
            return makeTimeString(minutes, seconds)
        }

        private fun makeTimeString(minutes: Int, seconds: Int): String = String.format("%02d:%02d", minutes, seconds)


    }

    fun startTimer() {
        timer?.cancel()
        timer = Timer()
        timer!!.scheduleAtFixedRate(TimeTask(), 1000, 1000)
    }

    fun stopTimer() {
        timer?.cancel()
    }




}

