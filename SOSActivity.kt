package com.example.she_shield

import android.os.Bundle
import android.os.CountDownTimer
import android.telephony.SmsManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SOSActivity : AppCompatActivity() {

    private lateinit var tvTimer: TextView
    private lateinit var btnSafe: Button
    private lateinit var countDownTimer: CountDownTimer

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sos)

        // LINKING THE UI TO CODE
        tvTimer = findViewById(R.id.tvTimer)
        btnSafe = findViewById(R.id.btnSafe)

        startSOSCountdown()

        btnSafe.setOnClickListener {
            if (::countDownTimer.isInitialized) {
                countDownTimer.cancel()
            }
            Toast.makeText(this, "Safe Corridor Reset", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun startSOSCountdown() {
        countDownTimer = object : CountDownTimer(120000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = millisUntilFinished / 1000
                tvTimer.text = "$secondsLeft"

                if (secondsLeft < 30) {
                    tvTimer.setTextColor(ContextCompat.getColor(this@SOSActivity, android.R.color.holo_red_dark))
                }
            }

            override fun onFinish() {
                tvTimer.text = "SOS!"
                sendEmergencySMS()
            }
        }.start()
    }

    private fun sendEmergencySMS() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val contactNumber = document.getString("contact_number")
                if (!contactNumber.isNullOrEmpty()) {
                    try {
                        val smsManager: SmsManager = SmsManager.getDefault()
                        smsManager.sendTextMessage(contactNumber, null, "EMERGENCY: SHE-SHIELD detected a route deviation. Check on me!", null, null)
                        Toast.makeText(this, "Emergency SMS Sent!", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "SMS Failed. Check permissions.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }
}