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
import java.io.ByteArrayInputStream
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
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


    private var currentDevice : Device? = null
    private val channels = ArrayList<Channel>()
    private val channelsAdapter = ChannelRecyclerAdapter(channels)
    private val replyQueue = LinkedBlockingQueue<ByteArray>()
    private val devices : LinkedList<Device> = LinkedList()
    private lateinit var currentMenuItem: MenuItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConnectionManager.registerListener(connectionEventListener)
        ModelPreferencesManager.with(this)
        setContentView(R.layout.activity_main)
        channels.add(Channel()) //TODO DEBUG
        //Log.i(TAG, "Channels size: " + channels.size)
        listChannels.adapter = channelsAdapter
        listChannels.layoutManager = LinearLayoutManager(this)
        listChannels.setHasFixedSize(true)
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




    private fun onDeviceSelected(device: Device) {
        currentDevice = device
    }

    private fun onNoDeviceSelected() {
        currentMenuItem.setTitle(R.string.device_name_holder)
    }

    private fun getChannels() : ArrayList<Channel>? {
        val cmd = byteArrayOf(0x80.toByte(), 0x86.toByte(), 1, 33)
        //write(cmd)
        val reply : ByteArray = replyQueue.poll(200, TimeUnit.MILLISECONDS) ?: return null
        val buffer = ByteArrayInputStream(reply)
        val count = buffer.read()
        val channels = ArrayList<Channel>(count)
        for (i in 0 until count) {
            
        }
        return channels
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

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onConnectionSetupComplete = {
                bluetoothDevice ->
                val connectedDevice = Device(bluetoothDevice)
                devices.add(connectedDevice)
                if (currentDevice == null) {
                    onDeviceSelected(connectedDevice)
                }
                runOnUiThread {
                    toast("Установлено соединение с ${connectedDevice.name}")
                }
            }
            onDisconnect = {
                bluetoothDevice ->
                var disconnectedDevice : Device? = null
                devices.forEach {
                    if (it.bluetoothDevice == bluetoothDevice) disconnectedDevice = it
                }
                if (disconnectedDevice != null) {
                    devices.remove(disconnectedDevice)
                    if (currentDevice == disconnectedDevice)
                        onNoDeviceSelected()

                    runOnUiThread {
                        toast("Потеряно соединение с ${disconnectedDevice!!.name}")
                    }
                }
            }
        }
    }
}