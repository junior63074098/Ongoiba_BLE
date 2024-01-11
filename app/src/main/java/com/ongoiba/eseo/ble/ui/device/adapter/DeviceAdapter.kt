package com.ongoiba.eseo.ble.ui.device.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ongoiba.eseo.ble.ui.device.Device
import com.ongoiba.eseo.ble.R

class DeviceAdapter(private val deviceList: ArrayList<Device>, private val onClick: ((selectedDevice: Device) -> Unit)? = null) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

    // Comment s'affiche ma vue
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        /**
         * Méthode appelée par la vue pour afficher les données
         * Ici nous faisons le lien entre les données et la vue (itemView)
         */
        fun showItem(device: Device, onClick: ((selectedDevice: Device) -> Unit)? = null) {
            itemView.findViewById<TextView>(R.id.idTextViewItem).text = device.name

            // Action au clique sur un élément de la liste
            if (onClick != null) {
                itemView.setOnClickListener {
                    onClick(device)
                }
            }
        }
    }

    // Retourne une « vue » / « layout » pour chaque élément de la liste
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_list, parent, false)
        return ViewHolder(view)
    }

    // Connect la vue ET la données, cette méthode est appelée à chaque fois que l'élément devient visible à l'écran
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.showItem(deviceList[position], onClick)
    }

    // Retourne le nombre d'éléments dans la liste
    override fun getItemCount(): Int {
        return deviceList.size
    }

}