package com.ongoiba.eseo.ble.ui.device.data

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ongoiba.eseo.ble.R

// Représente la vue
    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.idTextViewItem)
}
