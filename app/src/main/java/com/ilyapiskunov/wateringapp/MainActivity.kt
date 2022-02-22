package com.ilyapiskunov.wateringapp

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.*
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.alert
import org.jetbrains.anko.toast
import java.lang.Exception
import java.util.*
import java.util.concurrent.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    var bluetoothAdapter: BluetoothAdapter? = null
    val REQUEST_ENABLE_BT = 1
    val REQUEST_CONNECT_DEVICE = 2
    val REQUEST_GET_PERMISSION = 3

    val commandExecutor : ExecutorService = Executors.newSingleThreadExecutor()

    companion object {
        val EXTRA_ADDRESS: String = "Device_address"
    }


    private var currentDevice : SprinklerDevice? = null
    private val channels = ArrayList<Channel>()
    private val channelsAdapter = ChannelRecyclerAdapter(channels)
    private val replyQueue = LinkedBlockingQueue<ByteArray>()
    private val devices : LinkedList<SprinklerDevice> = LinkedList()
    private lateinit var currentMenuItem: MenuItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConnectionManager.registerListener(connectionEventListener)
        ModelPreferencesManager.with(this)
        setContentView(R.layout.activity_main)
        //Log.i(TAG, "Channels size: " + channels.size)
        listChannels.adapter = channelsAdapter
        listChannels.layoutManager = LinearLayoutManager(this)
        //listChannels.setHasFixedSize(true)

        val separator = DividerItemDecoration(this, LinearLayout.VERTICAL)
        separator.setDrawable(ContextCompat.getDrawable(this, R.drawable.divider)!!)
        listChannels.addItemDecoration(separator)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            toast("Bluetooth не поддерживается")
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BT)
        }

        btnWrite.setOnClickListener {
            currentDevice?.loadConfig() ?: alertDeviceNotConnected()
        }

        //DEBUG
        channels.add(Channel(Array(7) {false}, Array(7) {false}, AlarmTime(0, 0, 0), AlarmTime(0, 0, 0)))
    }

    override fun onDestroy() {
        super.onDestroy()
        commandExecutor.shutdown()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.current_device) {
            currentMenuItem = item
            if (devices.isNotEmpty()) {
                val deviceDialogBuilder = AlertDialog.Builder(this)
                deviceDialogBuilder.setItems(devices.map { device -> device.name }
                    .toTypedArray()) { _, index ->
                    val selected = devices[index]
                    if (currentDevice != selected)
                        onDeviceSelected(selected)

                }
            }
            else alertDeviceNotConnected()
            return true
        }
        else if (id == R.id.menu_device_list) {
            val deviceListIntent = Intent(this, DeviceListActivity::class.java)
            startActivityForResult(deviceListIntent, REQUEST_CONNECT_DEVICE)
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_ENABLE_BT -> {
                if (resultCode == Activity.RESULT_OK) {
                    Log.i(TAG, "Bluetooth has been enabled")
                } else {
                    finish()
                }
            }

            REQUEST_CONNECT_DEVICE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val address = data?.getStringExtra(EXTRA_ADDRESS)
                    if (address != null) {
                        //TODO: connect
                        val device = bluetoothAdapter!!.getRemoteDevice(address)
                        Log.i(TAG, "Connecting to " + device.name + "...")
                        ConnectionManager.connect(device, this)
                    }
                }
            }
        }
    }




    private fun onDeviceSelected(device: SprinklerDevice) {
        runOnUiThread {
            currentDevice = device
            currentMenuItem.title = device.name
            channels.clear()
            channels.addAll(device.channels)
            channelsAdapter.notifyDataSetChanged()

        }

    }

    private fun onNoDeviceSelected() {
        runOnUiThread {
            currentDevice = null
            currentMenuItem.setTitle(R.string.device_name_holder)
            channels.clear()
            channelsAdapter.notifyDataSetChanged()
        }

    }


    private fun alertDeviceNotConnected() {
        runOnUiThread {
            alert {
                title("Нет подключенных устройств")
                message("Выполните поиск и подключение")
                cancellable(false)
                positiveButton("OK") {}
            }.show()
        }
    }

    private fun alertError(message : String?) {
        runOnUiThread {
            alert {
                title("Ошибка")
                message(message?:"Неизвестная ошибка")
                cancellable(false)
                positiveButton("OK") {}
            }.show()
        }
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onConnectionSetupComplete = {
                bluetoothDevice ->
                    try {
                        val connectedDevice = SprinklerDevice(bluetoothDevice)
                        devices.add(connectedDevice)
                        onDeviceSelected(connectedDevice)
                        toast("Установлено соединение с ${connectedDevice.name}")

                    } catch (e: Exception) {
                        alertError(e.message)
                        ConnectionManager.disconnect(bluetoothDevice)
                    }

            }
            onDisconnect = {
                bluetoothDevice ->
                    var disconnectedDevice : SprinklerDevice? = null
                    devices.forEach {
                        if (it.bluetoothDevice == bluetoothDevice) disconnectedDevice = it
                    }
                    if (disconnectedDevice != null) {
                        devices.remove(disconnectedDevice!!)
                        toast("Потеряно соединение с ${disconnectedDevice!!.name}")
                        if (devices.isEmpty()) {
                            alertDeviceNotConnected()
                            onNoDeviceSelected()
                        }
                        else {
                            onDeviceSelected(devices[0])
                        }

                    }
            }
        }
    }
}