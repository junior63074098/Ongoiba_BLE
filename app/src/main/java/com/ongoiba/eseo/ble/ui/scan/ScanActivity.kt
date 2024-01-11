package com.ongoiba.eseo.ble.ui.scan


import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ongoiba.eseo.ble.ui.device.data.BluetoothLEManager
import com.ongoiba.eseo.ble.R
import com.ongoiba.eseo.ble.ui.device.Device
import com.ongoiba.eseo.ble.ui.device.adapter.DeviceAdapter

const val PERMISSION_REQUEST_LOCATION = 9999
const val REQUEST_ENABLE_BLE = 9997
class ScanActivity : AppCompatActivity() {
    // REQUEST Code de gestion
    companion object {
        fun getStartIntent(context: Context): Intent {
            return Intent(context, ScanActivity::class.java)
        }
    }
    // L'Adapter permettant de se connecter
    private var bluetoothAdapter: BluetoothAdapter? = null

    // La connexion actuellement établie
    private var currentBluetoothGatt: BluetoothGatt? = null

    // « Interface système nous permettant de scanner »
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    /**
     * Gestion du SCAN, recherche des device BLE à proximité
     */

    // Parametrage du scan BLE
    private val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

    // On ne retourne que les « Devices » proposant le bon UUID
    private var scanFilters: List<ScanFilter> = arrayListOf()

    // Variable de fonctionnement
    private var mScanning = false
    private val handler = Handler()

    // La liste des résultats
    private val bleDevicesFoundList = arrayListOf<Device>()

    private var rvDevices: RecyclerView? = null
    private var startScan: Button? = null
    private var currentConnexion: TextView? = null
    private var disconnect: Button? = null
    private var toggleLed: Button? = null
    private var sosLed: Button? = null
    private var ledStatus: TextView? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)
        setupRecycler()

        rvDevices = findViewById<RecyclerView>(R.id.rvBluetoothDevices)
        startScan = findViewById<Button>(R.id.LancerScan)
        toggleLed = findViewById<Button>(R.id.Toggle)
        sosLed = findViewById<Button>(R.id.SOS)
        currentConnexion = findViewById<TextView>(R.id.connecte)
        disconnect = findViewById<Button>(R.id.Deconnexion)

        startScan?.setOnClickListener {
            askForPermission()
        }

        disconnect?.setOnClickListener {
            // Appeler la bonne méthode
            disconnectFromCurrentDevice()
        }

        toggleLed?.setOnClickListener {
            // Appeler la bonne méthode
            toggleLed()
        }

        sosLed?.setOnClickListener {
            // Appeler la bonne méthode
            sendAnimation()
        }

    }
    override fun onResume() {
        super.onResume()

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            // Test si le téléphone est compatible BLE, si c'est pas le cas, on finish() l'activity
            Toast.makeText(this, getString(R.string.not_compatible), Toast.LENGTH_SHORT).show()
            finish()
        } else if (hasPermission() && locationServiceEnabled()) {
            // Lancer suite => Activation BLE + Lancer Scan
            setupBLE()
        } else if(!hasPermission()) {
            // On demande la permission
            askForPermission()
        } else {
            // On demande d'activer la localisation
            // Idéalement on demande avec un activité.
            // À vous de me proposer mieux (Une activité, une dialog, etc)
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        }
    }

    // apres l'acceptation de l'utilisateur
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED && locationServiceEnabled()) {
                // Permission OK => Lancer SCAN
                setupBLE()
            } else if (!locationServiceEnabled()) {
                // Inviter à activer la localisation
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            else {
                // Permission KO => Gérer le cas.
                // Vous devez ici modifier le code pour gérer le cas d'erreur (permission refusée)
                // Avec par exemple une Dialog
            }
        }
    }

    // verifie si le BLE est disponible / compatible
    private fun isLECompatible(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }
    // verifie les permissions
    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun askForPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_LOCATION)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_SCAN), PERMISSION_REQUEST_LOCATION)

        }
    }
    // verifie si la localisation est active
    private fun locationServiceEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // This is new method provided in API 28
            val lm = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.isLocationEnabled
        } else {
            // This is Deprecated in API 28
            val mode = Settings.Secure.getInt(this.contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF)
            mode != Settings.Secure.LOCATION_MODE_OFF
        }
    }
    /**
     * La méthode « registerForActivityResult » permet de gérer le résultat d'une activité.
     * Ce code est appelé à chaque fois que l'utilisateur répond à la demande d'activation du Bluetooth (visible ou non)
     * Si l'utilisateur accepte et donc que le BLE devient disponible, on lance le scan.
     * Si l'utilisateur refuse, on affiche un message d'erreur (Toast).
     */
    val registerForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            // Le Bluetooth est activé, on lance le scan
            scanLeDevice()
        } else {
            // Bluetooth non activé, vous DEVEZ gérer ce cas autrement qu'avec un Toast.
            Toast.makeText(this, "Bluetooth non activé", Toast.LENGTH_SHORT).show()
        }
    }
    // le bluetooth
    @SuppressLint("MissingPermission")
    private fun setupBLE() {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager?)?.let { bluetoothManager ->
            bluetoothAdapter = bluetoothManager.adapter
            if (bluetoothAdapter != null && bluetoothManager.adapter.isEnabled) {
                registerForResult.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                scanLeDevice()
            }
        }
    }
    @SuppressLint("MissingPermission")
    // le scan du device
    private fun scanLeDevice(scanPeriod: Long = 10000) {
        if (!mScanning) {
            bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

            // On vide la liste qui contient les devices actuellement trouvées
            bleDevicesFoundList.clear()

            mScanning = true

            // On lance une tache qui durera « scanPeriod » à savoir donc de base
            // 10 secondes
            handler.postDelayed({
                mScanning = false
                bluetoothLeScanner?.stopScan(leScanCallback)
                Toast.makeText(this, getString(R.string.scan_ended), Toast.LENGTH_SHORT).show()
            }, scanPeriod)

            // On lance le scan
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, leScanCallback)
        }
    }
    @SuppressLint("MissingPermission")
    // le scan gestion des resultat
    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            // C'est ici que nous allons créer notre « Device » et l'ajouter dans la dataSource de notre RecyclerView

            val device = Device(result.device.name, result.device.address, result.device)
            if (!device.name.isNullOrBlank() && !bleDevicesFoundList.contains(device)) {
                bleDevicesFoundList.add(device)
                //     Indique à l'adapter que nous avons ajouté un élément, il va donc se mettre à jour
                runOnUiThread{
                    findViewById<RecyclerView>(R.id.rvBluetoothDevices).adapter?.notifyDataSetChanged()
                }

                // }
            }
        }
    }
    /*
 * Méthode qui initialise le recycler view.
 */
    private fun setupRecycler() {
        val rvDevice = findViewById<RecyclerView>(R.id.rvBluetoothDevices) // Récupération du RecyclerView présent dans le layout
        rvDevice.layoutManager = LinearLayoutManager(this) // Définition du LayoutManager, Comment vont être affichés les éléments, ici en liste
        rvDevice.adapter = DeviceAdapter(bleDevicesFoundList) { device ->
            // Le code écrit ici sera appelé lorsque l'utilisateur cliquera sur un élément de la liste.
            // C'est un « callback », c'est-à-dire une méthode qui sera appelée à un moment précis.
            // Évidemment, vous pouvez faire ce que vous voulez. Nous nous connecterons plus tard à notre périphérique

            // Pour la démo, nous allons afficher un Toast avec le nom du périphérique choisi par l'utilisateur.
            //Toast.makeText(this@ScanActivity, "Clique sur $device", Toast.LENGTH_SHORT).show()
            BluetoothLEManager.currentDevice = device.device
            connectToCurrentDevice()
        }
    }
    @SuppressLint("MissingPermission")
    private fun connectToCurrentDevice() {
        BluetoothLEManager.currentDevice?.let { device ->
            Toast.makeText(this, "Connexion en cours … $device", Toast.LENGTH_SHORT).show()

            currentBluetoothGatt = device.connectGatt(
                this,
                false,
                BluetoothLEManager.GattCallback(
                    onConnect = {
                        // On indique à l'utilisateur que nous sommes correctement connecté
                        runOnUiThread {
                            // Nous sommes connecté au device, on active les notifications pour être notifié si la LED change d'état.

                            // À IMPLÉMENTER
                            // Vous devez appeler la méthode qui active les notifications BLE
                            enableListenBleNotify()

                            // On change la vue « pour être en mode connecté »
                            setUiMode(true)

                            // On sauvegarde dans les « LocalPréférence » de l'application le nom du dernier préphérique
                            // sur lequel nous nous sommes connecté

                            // À IMPLÉMENTER EN FONCTION DE CE QUE NOUS AVONS DIT ENSEMBLE
                        }
                    },
                    onNotify = {
                        runOnUiThread {
                            // VOUS DEVEZ APPELER ICI LA MÉTHODE QUI VA GÉRER LE CHANGEMENT D'ÉTAT DE LA LED DANS L'INTERFACE
                            // Si it (BluetoothGattCharacteristic) est pour l'UUID CHARACTERISTIC_NOTIFY_STATE
                            // Alors vous devez appeler la méthode qui va gérer le changement d'état de la LED
                            if (it.getUuid() == BluetoothLEManager.CHARACTERISTIC_NOTIFY_STATE) {
                                // À IMPLÉMENTER
                            } else if (it.getUuid() == BluetoothLEManager.CHARACTERISTIC_GET_COUNT) {
                                // À IMPLÉMENTER
                            } else if (it.getUuid() == BluetoothLEManager.CHARACTERISTIC_GET_WIFI_SCAN) {
                                // À IMPLÉMENTER
                            }
                        }
                    },
                    onDisconnect = { runOnUiThread { disconnectFromCurrentDevice() } })
            )
        }

    }
    /**
     * On demande la déconnexion du device
     */
    @SuppressLint("MissingPermission")
    private fun disconnectFromCurrentDevice() {
        currentBluetoothGatt?.disconnect()
        BluetoothLEManager.currentDevice = null
        setUiMode(false)
    }

    @SuppressLint("StringFormatInvalid","MissingPermission")
    private fun setUiMode(isConnected: Boolean) {
        if (isConnected) {
            // Connecté à un périphérique
            bleDevicesFoundList.clear()
            rvDevices?.visibility = View.GONE
            startScan?.visibility = View.GONE
            currentConnexion?.visibility = View.VISIBLE
            currentConnexion?.text = getString(R.string.connected_to, BluetoothLEManager.currentDevice?.name)
            disconnect?.visibility = View.VISIBLE
            toggleLed?.visibility = View.VISIBLE
            sosLed?.visibility = View.VISIBLE
        } else {
            // Non connecté, reset de la vue.
            rvDevices?.visibility = View.VISIBLE
            startScan?.visibility = View.VISIBLE
            ledStatus?.visibility = View.GONE
            currentConnexion?.visibility = View.GONE
            disconnect?.visibility = View.GONE
            toggleLed?.visibility = View.GONE
            sosLed?.visibility = View.GONE
        }
    }
    /**
     * Récupération de « service » BLE (via UUID) qui nous permettra d'envoyer / recevoir des commandes
     */
    private fun getMainDeviceService(): BluetoothGattService? {
        return currentBluetoothGatt?.let { bleGatt ->
            val service = bleGatt.getService(BluetoothLEManager.DEVICE_UUID)
            service?.let {
                return it
            } ?: run {
                Toast.makeText(this, getString(R.string.uuid_not_found), Toast.LENGTH_SHORT).show()
                return null;
            }
        } ?: run {
            Toast.makeText(this, getString(R.string.not_connected), Toast.LENGTH_SHORT).show()
            return null
        }
    }
    /**
     * On change l'état de la LED (via l'UUID de toggle)
     */
    @SuppressLint("MissingPermission")
    private fun toggleLed() {
        getMainDeviceService()?.let { service ->
            val toggleLed = service.getCharacteristic(BluetoothLEManager.CHARACTERISTIC_TOGGLE_LED_UUID)
            toggleLed.setValue("1")
            currentBluetoothGatt?.writeCharacteristic(toggleLed)
        }
    }
    @SuppressLint("MissingPermission")
    private fun enableListenBleNotify() {
        getMainDeviceService()?.let { service ->
            Toast.makeText(this, getString(R.string.enable_ble_notifications), Toast.LENGTH_SHORT).show()
            // Indique que le GATT Client va écouter les notifications sur le charactérisque
            val notificationStatus = service.getCharacteristic(BluetoothLEManager.CHARACTERISTIC_NOTIFY_STATE)
            val notificationLedCount = service.getCharacteristic(BluetoothLEManager.CHARACTERISTIC_GET_COUNT)
            val wifiScan = service.getCharacteristic(BluetoothLEManager.CHARACTERISTIC_GET_WIFI_SCAN)

            currentBluetoothGatt?.setCharacteristicNotification(notificationStatus, true)
            currentBluetoothGatt?.setCharacteristicNotification(notificationLedCount, true)
            currentBluetoothGatt?.setCharacteristicNotification(wifiScan, true)
        }
    }

   /* private fun handleToggleLedNotificationUpdate(characteristic: BluetoothGattCharacteristic) {
        if (characteristic.getStringValue(0).equals("on", ignoreCase = true)) {
            ledStatus.setImageResource(R.drawable.led_on)
        } else {
            ledStatus.setImageResource(R.drawable.led_off)
        }
    }*/

    private fun handleCountLedChangeNotificationUpdate(characteristic: BluetoothGattCharacteristic) {
        Toast.makeText(this, characteristic.getStringValue(0), Toast.LENGTH_SHORT).show()
    }

    /*
    private fun handleOnNotifyNotificationReceived(characteristic: BluetoothGattCharacteristic) {
     */
        // TODO : Vous devez ici récupérer la liste des réseaux WiFi disponibles et les afficher dans une liste.
        // Vous pouvez utiliser un RecyclerView pour afficher la liste des réseaux WiFi disponibles.
   // }
    @SuppressLint("MissingPermission")
    private fun sendAnimation() {
        getMainDeviceService()?.let { service ->
            val toggleLed = service.getCharacteristic(BluetoothLEManager.CHARACTERISTIC_TOGGLE_LED_UUID)
            toggleLed.setValue("101010101111011111000010101010")
            currentBluetoothGatt?.writeCharacteristic(toggleLed)
        }
    }
    @SuppressLint("MissingPermission")
    private fun sendDeviceName() {
        getMainDeviceService()?.let { service ->
            val setDeviceName = service.getCharacteristic(BluetoothLEManager.CHARACTERISTIC_SET_DEVICE_NAME)
            setDeviceName.setValue("LeNomDuDevice") // Le PMW- est ajouté automatiquement
            currentBluetoothGatt?.writeCharacteristic(setDeviceName)
        }
    }
}