package com.ongoiba.eseo.ble

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Représente la vue
    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.idTextViewItem)
}
