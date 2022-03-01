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
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.ilyapiskunov.wateringapp.Tools.toHex
import com.ilyapiskunov.wateringapp.ble.connection.ConnectionEventListener
import com.ilyapiskunov.wateringapp.ble.connection.ConnectionManager
import com.ilyapiskunov.wateringapp.ble.search.DeviceListActivity
import com.ilyapiskunov.wateringapp.model.DeviceEventListener
import com.ilyapiskunov.wateringapp.model.Channel
import com.ilyapiskunov.wateringapp.model.ChannelRecyclerAdapter
import com.ilyapiskunov.wateringapp.model.WateringDevice
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.anko.alert
import org.jetbrains.anko.toast
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    var bluetoothAdapter: BluetoothAdapter? = null
    val REQUEST_ENABLE_BT = 1
    val REQUEST_CONNECT_DEVICE = 2
    val REQUEST_GET_PERMISSION = 3


    companion object {
        val EXTRA_ADDRESS: String = "Device_address"
    }


    private var currentDevice : WateringDevice? = null
    private val channels = ArrayList<Channel>()
    private val messages = ArrayList<String>()
    private val channelsAdapter = ChannelRecyclerAdapter(channels)
    private lateinit var logAdapter : ArrayAdapter<String>
    private val devices : LinkedList<WateringDevice> = LinkedList()
    private lateinit var menuSelectDevice: MenuItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Locale.setDefault(Locale("ru", "RU"))
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
            currentDevice?.let {
                device ->
                device.loadConfig()
                device.setTime()
            } ?: alertDeviceNotConnected()
        }

        logAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, messages)
        log.adapter = logAdapter

        //DEBUG
        //channels.add(Channel(Array(7) {false}, Array(7) {false}, AlarmTime(0, 0, 0), AlarmTime(0, 0, 0)))

    }

    override fun onDestroy() {
        super.onDestroy()
        devices.forEach{ device -> device.disconnect() }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menuSelectDevice = menu?.findItem(R.id.current_device)!!
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.current_device) {
            if (devices.isNotEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("Устройства")
                    .setItems(devices.map { device -> device.name }
                        .toTypedArray()) { _, index ->
                        val selected = devices[index]
                        if (currentDevice != selected)
                            onDeviceSelected(selected)
                        else
                            selected.disconnect()

                    }.setPositiveButton("Поиск") { dialogInterface, i ->
                        startSearchActivity()
                    }.show()
            }
            else startSearchActivity()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun startSearchActivity() {
        val deviceListIntent = Intent(this, DeviceListActivity::class.java)
        startActivityForResult(deviceListIntent, REQUEST_CONNECT_DEVICE)
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




    private fun onDeviceSelected(device: WateringDevice) {
        runOnUiThread {
            currentDevice = device
            channelsAdapter.setCurrentDevice(device)
            menuSelectDevice.title = device.name
            tvVoltage.text = getString(R.string.voltage_format, device.voltage)
            tvWaterLevel.text = getString(R.string.water_level_format, device.waterLevel)
            channels.clear()
            channels.addAll(device.channels)
            channelsAdapter.notifyDataSetChanged()

        }

    }

    private fun onNoDeviceSelected() {
        runOnUiThread {
            currentDevice = null
            channelsAdapter.setCurrentDevice(null)
            menuSelectDevice.setTitle(R.string.device_name_holder)
            tvVoltage.text = ""
            tvWaterLevel.text = ""
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

    private val deviceEventListener by lazy {
        DeviceEventListener().apply {

            onCommandStart = {
                name ->
                journal("Команда $name")
            }

            onCommandSuccess = {
                runOnUiThread {
                    toast("OK")
                }
            }

            onCommandError = {
                name, exception ->
                exception.printStackTrace()
                alertError(exception.message)
            }
        }
    }

    private fun journal(message: String) {
        runOnUiThread {
            messages.add(message)
            logAdapter.notifyDataSetChanged()
        }
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onConnectionSetupComplete = {
                bluetoothDevice ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        journal("Установлено соединение с ${bluetoothDevice.name}")
                        val connectedDevice = WateringDevice(bluetoothDevice, deviceEventListener)
                        devices.add(connectedDevice)
                        onDeviceSelected(connectedDevice)
                        //toast("Установлено соединение с ${connectedDevice.name}")

                    } catch (e: Exception) {
                        e.printStackTrace()
                        alertError(e.message)
                        ConnectionManager.disconnect(bluetoothDevice)
                    }
                }

            }
            onDisconnect = {
                bluetoothDevice ->
                CoroutineScope(Dispatchers.IO).launch {
                    journal("Потеряно соединение с ${bluetoothDevice.name}")
                    var disconnectedDevice: WateringDevice? = null
                    devices.forEach {
                        if (it.bluetoothDevice == bluetoothDevice) disconnectedDevice = it
                    }
                    if (disconnectedDevice != null) {
                        devices.remove(disconnectedDevice!!)
                        disconnectedDevice!!.unregisterListener()
                        //toast("Потеряно соединение с ${disconnectedDevice!!.name}")
                        if (devices.isEmpty()) {
                            alertDeviceNotConnected()
                            onNoDeviceSelected()
                        } else {
                            onDeviceSelected(devices[0])
                        }

                    }
                }
            }
            onRead = {
                bluetoothDevice, bytes ->
                runOnUiThread {
                    journal("RX: ${bytes.toHex()}")
                }
            }

            onWrite = {
                bluetoothDevice, bytes ->
                journal("TX: ${bytes.toHex()}")
            }
        }
    }
}