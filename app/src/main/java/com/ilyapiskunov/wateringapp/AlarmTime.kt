package com.ilyapiskunov.wateringapp

import com.ilyapiskunov.wateringapp.Tools.modNeg

data class AlarmTime(var hours : Int, var minutes: Int, var seconds : Int) {

    enum class TimeField {
        HOURS,
        MINUTES,
        SECONDS
    }

    private fun changeValue(field : TimeField, inc : Int) : Int {
        return when (field) {
            TimeField.HOURS -> {
                hours = (hours + inc).modNeg(24)
                hours
            }
            TimeField.MINUTES -> {
                minutes = (minutes + inc).modNeg(60)
                minutes
            }
            TimeField.SECONDS -> {
                seconds = (seconds + inc).modNeg(60)
                seconds
            }
        }
    }

    fun incHours() : Int {
        return changeValue(TimeField.HOURS, 1)
    }

    fun incMinutes() : Int{
        return changeValue(TimeField.MINUTES, 1)
    }

    fun incSeconds() : Int {
        return changeValue(TimeField.SECONDS, 1)
    }

    fun decHours() : Int{
        return changeValue(TimeField.HOURS, -1)
    }

    fun decMinutes() : Int{
        return changeValue(TimeField.MINUTES, -1)
    }

    fun decSeconds() : Int {
        return changeValue(TimeField.SECONDS, -1)
    }

    fun toString(fieldType: TimeField) : String {
        val field = when (fieldType) {
            TimeField.HOURS -> hours
            TimeField.MINUTES -> minutes
            TimeField.SECONDS -> seconds
        }
        return String.format("%02d", field)
    }

    override fun toString(): String {
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }


}