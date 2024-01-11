package com.ongoiba.eseo.ble.ui.main

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.google.android.material.snackbar.Snackbar
import com.ongoiba.eseo.ble.R
import com.ongoiba.eseo.ble.ui.scan.ScanActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnScan).setOnClickListener{
            Snackbar.make(findViewById(android.R.id.content), "Activité scan", Snackbar.LENGTH_LONG).setAction("Touche moi") {
            }.show()
            startActivity(ScanActivity.getStartIntent(this))
        }
    }

}