package com.ilyapiskunov.wateringapp

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.*
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.ilyapiskunov.wateringapp.Tools.toHex
import com.ilyapiskunov.wateringapp.ble.connection.ConnectionEventListener
import com.ilyapiskunov.wateringapp.ble.connection.ConnectionManager
import com.ilyapiskunov.wateringapp.ble.search.DeviceListActivity
import com.ilyapiskunov.wateringapp.journal.JournalActivity
import com.ilyapiskunov.wateringapp.journal.JournalMessage
import com.ilyapiskunov.wateringapp.model.*
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
    private val messages = ArrayList<JournalMessage>()
    private val channelsAdapter = ChannelRecyclerAdapter(channels)
    private val devices : LinkedList<WateringDevice> = LinkedList()
    private lateinit var menuCurrentDevice: MenuItem

    private var isBusy = false
        set(value) {
            field = value
            runOnUiThread {
                progressBar.visibility = if (value) View.VISIBLE else View.GONE
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //supportActionBar?.setDisplayShowTitleEnabled(false)
        Locale.setDefault(Locale("ru", "RU"))
        ConnectionManager.registerListener(connectionEventListener)
        ModelPreferencesManager.with(this)
        setContentView(R.layout.activity_main)

        list_channels.adapter = channelsAdapter
        list_channels.layoutManager = LinearLayoutManager(this)

        val separator = DividerItemDecoration(this, LinearLayout.VERTICAL)
        separator.setDrawable(ContextCompat.getDrawable(this, R.drawable.divider)!!)
        list_channels.addItemDecoration(separator)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            toast("Bluetooth не поддерживается")
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BT)
        }

        btn_write.setOnClickListener {
            currentDevice?.let {
                device ->
                device.loadConfig()
                device.setTime(GregorianCalendar.getInstance())
            } ?: alertDeviceNotConnected()
        }

        journal("Старт")

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
        menuCurrentDevice = menu?.findItem(R.id.menu_current_device)!!
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            /**
             *  Rename current device
             */
            R.id.menu_current_device -> {
                currentDevice?.let { device ->
                    val input = EditText(this)
                    input.inputType = InputType.TYPE_CLASS_TEXT
                    input.setText(device.getName(), TextView.BufferType.EDITABLE)
                    AlertDialog.Builder(this)
                        .setTitle("Введите новое имя")
                        .setView(input)
                        .setPositiveButton("ОК") { dialog, i ->
                            device.setName(input.text.toString())
                        }
                        .setNegativeButton("Отмена") { dialog, i ->
                            dialog.cancel()
                        }
                        .show()
                } ?: alertDeviceNotConnected()
            }
            /**
             *  Show connected devices to choose from, if none open search activity
             */
            R.id.menu_search -> {
                if (devices.isNotEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Устройства")
                        .setItems(devices.map { device -> device.getName() }
                            .toTypedArray()) { _, index ->
                            val selected = devices[index]
                            if (currentDevice != selected)
                                onDeviceSelected(selected)
                            else
                                selected.disconnect()

                        }.setPositiveButton("Поиск") { dialog, i ->
                            startSearchActivity()
                        }.setNegativeButton("Отмена") { dialog, i ->
                            dialog.cancel()
                        }
                        .show()
                } else startSearchActivity()
                return true
            }
            /**
             *  Refresh device state
             */
            R.id.menu_get_state -> {
                currentDevice?.getState() ?: alertDeviceNotConnected()
            }
            /**
             *  Open journal
             */
            R.id.menu_journal -> {
                startJournalActivity()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun startSearchActivity() {
        val deviceListIntent = Intent(this, DeviceListActivity::class.java)
        startActivityForResult(deviceListIntent, REQUEST_CONNECT_DEVICE)
    }

    private fun startJournalActivity() {
        val journalIntent = Intent(this, JournalActivity::class.java)
        journalIntent.putExtra("messages", messages)
        startActivity(journalIntent)
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



    @SuppressLint("NotifyDataSetChanged")
    private fun onDeviceSelected(device: WateringDevice) {
        runOnUiThread {
            btn_write.visibility = View.VISIBLE
            currentDevice = device
            channelsAdapter.setCurrentDevice(device)
            menuCurrentDevice.title = device.getName()
            tv_voltage.text = getString(R.string.voltage_format, device.getVoltage())
            tv_water_level.text = getString(R.string.water_level_format, device.getWaterLevel())
            tv_version_id.text = getString(R.string.device_version_id_format, device.mcuVersion.toHex(""), device.mcuId.toHex(""))
            channels.clear()
            channels.addAll(device.channels)
            channelsAdapter.notifyDataSetChanged()

        }

    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onNoDeviceSelected() {
        runOnUiThread {
            btn_write.visibility = View.GONE
            currentDevice = null
            channelsAdapter.setCurrentDevice(null)
            menuCurrentDevice.setTitle(R.string.menu_current_device)
            tv_voltage.text = ""
            tv_water_level.text = ""
            tv_version_id.text = ""
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

            onCommandStart = { device, cmd ->
                journal(device.getName(), "Команда ${cmd.name}")
            }

            onCommandSuccess = { device, cmd ->
                journal(device.getName(), "Команда ${cmd.name} выполнена")

                if (currentDevice == device)
                    runOnUiThread {
                        when (cmd) {
                            WateringDevice.Command.GET_STATE -> {
                                tv_voltage.text = getString(R.string.voltage_format, device.getVoltage())
                                tv_water_level.text = getString(R.string.water_level_format, device.getWaterLevel())
                            }

                            WateringDevice.Command.SET_NAME -> {
                                menuCurrentDevice.title = device.getName()
                            }
                        }
                        toast("OK")
                    }
            }

            onCommandError = { device, cmd, exception ->
                journal(device.getName(), "Ошибка при выполнении команды ${cmd.name} - ${exception.message}")
                exception.printStackTrace()
                alertError(exception.message)
            }
        }
    }



    private fun journal(message: String) {
        journal("Система", message)
    }

    private fun journal(header: String, message: String) {
        messages.add(JournalMessage(header, message))
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onConnectionSetupComplete = {
                bluetoothDevice ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        isBusy = true
                        journal("Установлено соединение с ${bluetoothDevice.address}")
                        val connectedDevice = WateringDevice(bluetoothDevice, deviceEventListener)
                        connectedDevice.getState()
                        devices.add(connectedDevice)
                        onDeviceSelected(connectedDevice)
                        //toast("Установлено соединение с ${connectedDevice.name}")

                    } catch (e: Exception) {
                        e.printStackTrace()
                        alertError(e.message)
                        ConnectionManager.disconnect(bluetoothDevice)
                    }
                    finally {
                        isBusy = false
                    }
                }

            }
            onDisconnect = {
                bluetoothDevice ->
                CoroutineScope(Dispatchers.IO).launch {
                    journal("Потеряно соединение с ${bluetoothDevice.address}")
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
                    journal("RX", bytes.toHex(" "))
                }
            }

            onWrite = {
                bluetoothDevice, bytes ->
                journal("TX", bytes.toHex(" "))
            }
        }
    }
}