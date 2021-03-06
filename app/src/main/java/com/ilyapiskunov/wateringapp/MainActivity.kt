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
import android.widget.Toast
import androidx.core.content.ContextCompat
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
    private val channelsAdapter = ChannelRecyclerAdapter(this, channels)
    private val devices : LinkedList<WateringDevice> = LinkedList()
    private lateinit var menuCurrentDevice: MenuItem
    private var currentToast : Toast? = null

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

/************************** DEBUG *********************************************/
        val properties = Properties()
        properties.load(baseContext.assets.open("app.properties"))
        val debug = properties.getProperty("debug").toBoolean()
        if (debug) {
            channels.add(Channel(Array(7){false}, Array(7){false}, ChannelControlTimer(0, 0, 0), ChannelControlTimer(0,0,0) ))
        }
/******************************************************************************/


        list_channels.adapter = channelsAdapter
        list_channels.layoutManager = LinearLayoutManager(this)

        val separator = DividerItemDecoration(this, LinearLayout.VERTICAL)
        separator.setDrawable(ContextCompat.getDrawable(this, R.drawable.divider)!!)
        list_channels.addItemDecoration(separator)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            toast("Bluetooth ???? ????????????????????????????")
            return
        }



        btn_write.setOnClickListener {
            currentDevice?.loadConfig() ?: alertDeviceNotConnected()
        }

        journal("??????????")

        if (!bluetoothAdapter!!.isEnabled) {
            val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BT)
        }
        else startSearchActivity()
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
                    input.setText(device.name, TextView.BufferType.EDITABLE)
                    AlertDialog.Builder(this)
                        .setTitle("?????????????? ?????????? ??????")
                        .setView(input)
                        .setPositiveButton("????") { dialog, i ->
                            device.setName(input.text.toString())
                        }
                        .setNegativeButton("????????????") { dialog, i ->
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
                        .setTitle("????????????????????")
                        .setItems(devices.map { device -> device.name }
                            .toTypedArray()) { _, index ->
                            val selected = devices[index]
                            if (currentDevice != selected)
                                onDeviceSelected(selected)
                            else
                                selected.disconnect()

                        }.setPositiveButton("??????????") { dialog, i ->
                            startSearchActivity()
                        }.setNegativeButton("????????????") { dialog, i ->
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
            /**
             * Show device info
             */
            R.id.menu_device_info -> {
                currentDevice?.let {
                    AlertDialog.Builder(this)
                        .setMessage(getString(R.string.device_version_id_format, it.mcuVersion.toHex(""), it.mcuId))
                        .setPositiveButton("OK") { dialog, i ->
                            dialog.cancel()
                        }
                        .show()
                } ?: alertDeviceNotConnected()
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
                    startSearchActivity()
                } else {
                    finish()
                }
            }

            REQUEST_CONNECT_DEVICE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val address = data?.getStringExtra(EXTRA_ADDRESS)
                    if (address != null) {
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
            menuCurrentDevice.title = device.name
            tv_voltage.text = getString(R.string.voltage_format, device.voltage)
            tv_water_level.text = getString(R.string.water_level_format, device.waterLevel)
            channels.forEach { channel -> channel.stopTimer() }
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
            channels.clear()
            channelsAdapter.notifyDataSetChanged()
        }

    }


    private fun alertDeviceNotConnected() {
        runOnUiThread {
            alert {
                title("?????? ???????????????????????? ??????????????????")
                message("?????????????????? ?????????? ?? ??????????????????????")
                cancellable(false)
                positiveButton("OK") {}
            }.show()
        }
    }

    private fun alertError(message : String?) {
        runOnUiThread {
            alert {
                title("????????????")
                message(message?:"?????????????????????? ????????????")
                cancellable(false)
                positiveButton("OK") {}
            }.show()
        }
    }



    private val deviceEventListener by lazy {
        DeviceEventListener().apply {

            onCommandStart = { device, cmd ->
                if (cmd != WateringDevice.Command.CONNECTION_PROBE)
                    journal(device.name, "?????????????? ${cmd.name}")
            }

            onCommandSuccess = { device, cmd ->
                if (cmd != WateringDevice.Command.CONNECTION_PROBE) {

                    journal(device.name, "?????????????? ${cmd.name} ??????????????????")

                    if (currentDevice == device)
                        runOnUiThread {
                            when (cmd) {
                                WateringDevice.Command.GET_STATE -> {
                                    tv_voltage.text =
                                        getString(R.string.voltage_format, device.voltage)
                                    tv_water_level.text = getString(
                                        R.string.water_level_format,
                                        device.waterLevel
                                    )
                                }

                                WateringDevice.Command.SET_NAME -> {
                                    menuCurrentDevice.title = device.name
                                }
                            }
                            toastOnce("OK")
                        }
                }

            }

            onCommandError = { device, cmd, exception ->
                journal(device.name, "???????????? ?????? ???????????????????? ?????????????? ${cmd.name} - ${exception.message}")
                exception.printStackTrace()
                alertError(exception.message)

                if (cmd == WateringDevice.Command.CONNECTION_PROBE) {
                    device.disconnect()
                }
            }

            onRead = {
                    device, bytes ->
                journal(device.name, "RX: " + bytes.toHex(" "))

            }

            onWrite = {
                    device, bytes ->
                journal(device.name, "TX: " + bytes.toHex(" "))
            }
        }
    }

    //cancel previous toast
    private fun toastOnce(message: String) {
        currentToast?.cancel()
        currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        currentToast!!.show()

    }

    private fun journal(message: String) = journal("??????????????", message)


    private fun journal(header: String, message: String) = messages.add(JournalMessage(header, message))


    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onConnectionSetupComplete = {
                bluetoothDevice ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        isBusy = true
                        journal("?????????????????????? ???????????????????? ?? ${bluetoothDevice.address}")
                        val connectedDevice = WateringDevice(bluetoothDevice, deviceEventListener)
                        connectedDevice.getState()
                        connectedDevice.setTime(GregorianCalendar.getInstance())
                        devices.add(connectedDevice)
                        onDeviceSelected(connectedDevice)
                        //toast("?????????????????????? ???????????????????? ?? ${connectedDevice.name}")

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
                    journal("???????????????? ???????????????????? ?? ${bluetoothDevice.address}")
                    var disconnectedDevice: WateringDevice? = null
                    devices.forEach {
                        if (it.bluetoothDevice == bluetoothDevice) disconnectedDevice = it
                    }
                    disconnectedDevice?.let {
                        devices.remove(it)
                        it.shutdown()
                        //toast("???????????????? ???????????????????? ?? ${disconnectedDevice!!.name}")
                        if (devices.isEmpty()) {
                            alertDeviceNotConnected()
                            onNoDeviceSelected()
                        } else {
                            onDeviceSelected(devices[0])
                        }
                    }
                }
            }

        }
    }
}