package com.ongoiba.eseo.ble.ui.device

import android.bluetooth.BluetoothDevice

// Représente les données
data class Device (
    var name: String?,
    var mac: String?,
    var device: BluetoothDevice
) {

    override fun equals(other: Any?): Boolean {
        // On compare les MAC, pour ne pas ajouté deux fois le même device dans la liste.
        return other is Device && other.mac == this.mac
    }
}