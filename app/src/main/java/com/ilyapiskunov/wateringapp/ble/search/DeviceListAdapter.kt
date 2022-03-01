package com.ilyapiskunov.wateringapp.ble.search

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ilyapiskunov.wateringapp.R
import kotlinx.android.synthetic.main.device_list_item.view.*

class DeviceListAdapter(private val devices : List<BluetoothDevice>) :
    RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder>() {

    var onItemClick: ((BluetoothDevice) -> Unit)? = null

    inner class DeviceViewHolder(itemView : View) : RecyclerView.ViewHolder(itemView) {
        val imgDeviceStatus : ImageView = itemView.imgDeviceStatus
        val tvDeviceName: TextView = itemView.tvDeviceName
        val tvDeviceAddress : TextView = itemView.tvDeviceAddress

        init {
            itemView.setOnClickListener { onItemClick?.invoke(devices[absoluteAdapterPosition]) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.device_list_item, parent, false)
        return DeviceViewHolder(itemView)
    }

    override fun getItemCount(): Int {
        return devices.size
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]

        holder.tvDeviceName.text = device.name
        holder.tvDeviceAddress.text = device.address
    }

    fun updateStatus(position : Int) {

    }
}