package com.example.she_shield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat // CRITICAL IMPORT
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var btnIAmSafe: Button

    // 1. SIGNAL RECEIVER
    // Inside MainActivity.kt

    private val safetyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "SHE_SHIELD_UNSAFE") {
                // THE MOMENT OF TRUTH: Force the UI to show
                btnIAmSafe.visibility = View.VISIBLE
                btnIAmSafe.text = "I AM SAFE (15)" // Initial text

                // Bring the app to the front if it's minimized
                val intentToFront = Intent(context, MainActivity::class.java)
                intentToFront.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(intentToFront)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val customToolbar = findViewById<Toolbar>(R.id.custom_toolbar)
        setSupportActionBar(customToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        auth = FirebaseAuth.getInstance()

        val btnSos = findViewById<Button>(R.id.btnSos)
        val btnLiveTracking = findViewById<Button>(R.id.btnLiveTracking)
        val btnEmergencyContacts = findViewById<Button>(R.id.btnEmergencyContacts)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        btnIAmSafe = findViewById(R.id.btnIAmSafe)

        startSafetyMonitorService()

        btnIAmSafe.setOnClickListener {
            val stopIntent = Intent(this, SafetyMonitorService::class.java)
            stopIntent.action = "STOP_PROTECTION"
            startService(stopIntent)
            btnIAmSafe.visibility = View.GONE
            Toast.makeText(this, "Protection deactivated. You are safe!", Toast.LENGTH_SHORT).show()
        }

        btnSos.setOnClickListener {
            startActivity(Intent(this, SOSActivity::class.java))
        }

        btnLiveTracking.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }

        btnEmergencyContacts.setOnClickListener {
            startActivity(Intent(this, ContactsActivity::class.java))
        }

        btnLogout.setOnClickListener {
            stopSafetyMonitorService()
            auth.signOut()
            val loginIntent = Intent(this, LoginActivity::class.java)
            loginIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(loginIntent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("SHE_SHIELD_UNSAFE")

        // THIS IS THE FIX: Using ContextCompat satisfies the linter perfectly
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            safetyReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(safetyReceiver)
    }

    private fun startSafetyMonitorService() {
        val serviceIntent = Intent(this, SafetyMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopSafetyMonitorService() {
        val serviceIntent = Intent(this, SafetyMonitorService::class.java)
        serviceIntent.action = "STOP_PROTECTION"
        startService(serviceIntent)
    }
}
