package com.example.she_shield

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth

class SafetyMonitorService : Service() {

    private var countdownTimer: CountDownTimer? = null
    private var isAlertActive = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_PROTECTION") {
            stopEverything()
            return START_NOT_STICKY
        }

        // 1. Mandatory Foreground Start for Android 16
        val notification = createNotification("Guardian Active: Monitoring Corridor")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, notification)
        }

        startSafetyLogic()
        return START_STICKY
    }

    private fun startSafetyLogic() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val sharedPref = getSharedPreferences("SHE_SHIELD_PREFS", MODE_PRIVATE)

        val hLat = sharedPref.getFloat("home_lat", 0f).toDouble()
        val hLng = sharedPref.getFloat("home_lng", 0f).toDouble()
        val wLat = sharedPref.getFloat("work_lat", 0f).toDouble()
        val wLng = sharedPref.getFloat("work_lng", 0f).toDouble()

        if (hLat == 0.0) return

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val currentLoc = locationResult.lastLocation ?: return
                    val res = FloatArray(1)

                    Location.distanceBetween(currentLoc.latitude, currentLoc.longitude, hLat, hLng, res)
                    val dHome = res[0]
                    Location.distanceBetween(currentLoc.latitude, currentLoc.longitude, wLat, wLng, res)
                    val dWork = res[0]

                    val minD = minOf(dHome.toInt(), dWork.toInt())
                    updateNotification("Distance to Safety: ${minD}m")

                    // THE AUTOMATIC TRIGGER (Mumbai Mock)
                    if (dHome > 100 && dWork > 100) {
                        if (!isAlertActive) {
                            startTimer(currentLoc)
                            triggerEmergencyUI() // THIS WAKES THE SCREEN
                        }
                    } else if (isAlertActive) {
                        cancelTimer()
                    }
                }
            }, Looper.getMainLooper())
        } catch (e: SecurityException) { stopSelf() }
    }

    // ANDROID 16 FULL-SCREEN BYPASS
    private fun triggerEmergencyUI() {
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, "safety_channel")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("⚠️ EMERGENCY DETECTED")
            .setContentText("SOS Countdown Started! Tap if safe.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true) // FORCES DISPLAY ON ONE UI 8.0
            .setOngoing(true)

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, builder.build())
    }

    private fun startTimer(loc: Location) {
        isAlertActive = true
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        countdownTimer = object : CountDownTimer(15000, 1000) {
            override fun onTick(ms: Long) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else { vibrator.vibrate(200) }

                val intent = Intent("SHE_SHIELD_UNSAFE").apply {
                    setPackage(packageName)
                    putExtra("seconds_left", (ms / 1000).toInt())
                }
                sendBroadcast(intent)
            }

            override fun onFinish() {
                sendEmergencySMS("https://www.google.com/maps?q=${loc.latitude},${loc.longitude}")
            }
        }.start()
    }

    private fun sendEmergencySMS(url: String) {
        val sharedPref = getSharedPreferences("SHE_SHIELD_PREFS", MODE_PRIVATE)
        val num = sharedPref.getString("emergency_number", null)
        val name = FirebaseAuth.getInstance().currentUser?.displayName ?: "User"

        if (num != null) {
            try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    this.getSystemService(android.telephony.SmsManager::class.java)
                } else { android.telephony.SmsManager.getDefault() }

                smsManager.sendTextMessage(num, null, "EMERGENCY: $name is off-track. Location: $url", null, null)
            } catch (e: Exception) { Log.e("SMS", "Failed: ${e.message}") }
        }
    }

    private fun stopEverything() {
        countdownTimer?.cancel()
        isAlertActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(txt: String): Notification {
        val channelId = "safety_channel"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // ONE UI 8.0 REQUIRES IMPORTANCE_HIGH FOR FULL_SCREEN_INTENT
            val channel = NotificationChannel(channelId, "Safety Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Critical Safety Monitoring"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // THIS IS THE CRITICAL ADDITION FOR ANDROID 16
                setBypassDnd(true)
            }
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SHE-SHIELD Active")
            .setContentText(txt)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_MAX) // MAX is required for pop-up
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(txt: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, createNotification(txt))
    }

    private fun cancelTimer() {
        isAlertActive = false
        countdownTimer?.cancel()
        updateNotification("Monitoring safety corridor...")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
