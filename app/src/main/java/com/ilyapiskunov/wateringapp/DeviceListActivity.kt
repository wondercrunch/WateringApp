package com.ilyapiskunov.wateringapp

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.*
import android.bluetooth.le.ScanSettings.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.device_list_layout.*
import org.jetbrains.anko.alert
import org.jetbrains.anko.toast


class DeviceListActivity : Activity() {

    private val REQUEST_GET_PERMISSION = 3

    lateinit var bluetoothAdapter: BluetoothAdapter
    lateinit var bleScanner : BluetoothLeScanner
    lateinit var pairedDevices: ArrayList<BluetoothDevice>
    lateinit var newDevices: ArrayList<BluetoothDevice>
    private val devices = ArrayList<BluetoothDevice>()
    private val devicesAdapter = DeviceListAdapter(devices)
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(SCAN_MODE_LOW_LATENCY)
        //.setCallbackType(CALLBACK_TYPE_FIRST_MATCH)
        .build()

    private val scanFilter = ScanFilter.Builder()
        .setDeviceName("JDY-23")
        .build()

    private var isScanning = false
        set(value) {
            field = value
            runOnUiThread { btn_scan.text = if (value) "Стоп" else "Поиск" }
        }

    private val isLocationPermissionGranted
        get() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
                PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.device_list_layout)
        list_devices.adapter = devicesAdapter
        list_devices.layoutManager = LinearLayoutManager(this)
        devicesAdapter.onItemClick = { bluetoothDevice ->
            if (isScanning) stopBleScan()
            intent = Intent()
            intent.putExtra(MainActivity.EXTRA_ADDRESS, bluetoothDevice.address)

            setResult(RESULT_OK, intent)
            finish()
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bleScanner = bluetoothAdapter.bluetoothLeScanner
        btn_scan.setOnClickListener {
            if (isScanning)
                stopBleScan()
            else
                startBleScan()
        }


    }

    override fun onBackPressed() {
        if (isScanning) stopBleScan()
        super.onBackPressed()
    }

    private fun startBleScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isLocationPermissionGranted) {
            requestLocationPermission()
        }
        else {
            devices.clear()
            devicesAdapter.notifyDataSetChanged()
            bleScanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
            isScanning = true
        }
    }

    private fun stopBleScan() {
        bleScanner.stopScan(scanCallback)
        isScanning = false
    }

    private fun requestLocationPermission() {
        if (isLocationPermissionGranted) {
            return
        }
        runOnUiThread {
            alert {
                title("Необходимо разрешение")
                message("Начиная с версии 6.0 для сканирования BLE необходимо разрешение на геолокацию")
                cancellable(false)
                positiveButton(android.R.string.ok) {
                    requestPermission(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        REQUEST_GET_PERMISSION
                    )
                }
            }.show()
        }
    }

    private fun Activity.requestPermission(permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_GET_PERMISSION -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    requestLocationPermission()
                } else {
                    startBleScan()
                }
            }
        }
    }

    private val scanCallback : ScanCallback = object : ScanCallback() {
        override fun onScanFailed(errorCode: Int) {
            toast("Ошибка")
            super.onScanFailed(errorCode)
            stopBleScan()
        }

        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result!!.device
            if (!device.address.isNullOrBlank() && !devices.contains(device)) {
                devices.add(device)
                devicesAdapter.notifyItemInserted(devices.size-1)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
        }
    }
}