package com.ilyapiskunov.wateringapp.ble.connection

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.ilyapiskunov.wateringapp.Tools.toHex

sealed class Operation {
    abstract val device : BluetoothDevice
}

data class Connect(override val device: BluetoothDevice, val context: Context) : Operation()

data class Disconnect(override val device: BluetoothDevice) : Operation()

data class Write(override val device: BluetoothDevice, val data : ByteArray) : Operation() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Write

        if (device != other.device) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = device.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "Write(model=$device, data=${data.toHex()})"
    }


}
