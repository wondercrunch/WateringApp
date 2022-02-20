package com.ilyapiskunov.wateringapp

import java.util.*


data class Channel (val week1 : Array<Boolean>, val week2 : Array<Boolean>,
                    val dateOn : Calendar, val dateOff : Calendar,
                    val week1SelType : Int, val week2SelType : Int){

    /**
     * Debug constructor
     */
    constructor() : this (Array(7){false}, Array(7){false},
                            Calendar.getInstance(), Calendar.getInstance(),
                            0, 0)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Channel

        if (!week1.contentEquals(other.week1)) return false
        if (!week2.contentEquals(other.week2)) return false
        if (dateOn != other.dateOn) return false
        if (dateOff != other.dateOff) return false
        if (week1SelType != other.week1SelType) return false
        if (week2SelType != other.week2SelType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = week1.contentHashCode()
        result = 31 * result + week2.contentHashCode()
        result = 31 * result + dateOn.hashCode()
        result = 31 * result + dateOff.hashCode()
        result = 31 * result + week1SelType
        result = 31 * result + week2SelType
        return result
    }

}