package com.example.she_shield

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var tvTitle: TextView
    private lateinit var tvDesc: TextView
    private lateinit var btnConfirm: ExtendedFloatingActionButton

    private var homeMarker: Marker? = null
    private var workMarker: Marker? = null
    private var polyline: Polyline? = null

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        tvTitle = findViewById(R.id.tvStatusTitle)
        tvDesc = findViewById(R.id.tvStatusDescription)
        btnConfirm = findViewById(R.id.btnConfirmPath)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        btnConfirm.setOnClickListener { savePathToFirebase() }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // 1. DIVE INTO THE CITY (Visakhapatnam Coordinates)
        val vskp = LatLng(17.6868, 83.2185)

        // 15f is the "Sweet Spot" for street-level picking
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(vskp, 15f))

        // 2. ADD STYLISH UI CONTROLS
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true

        mMap.setOnMapClickListener { latLng ->
            handleMapClick(latLng)
        }
    }

    private fun handleMapClick(latLng: LatLng) {
        if (homeMarker == null) {
            // STEP 1: SET HOME (GREEN)
            homeMarker = mMap.addMarker(MarkerOptions()
                .position(latLng)
                .title("Home")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))

            tvTitle.text = "Set Destination"
            tvDesc.text = "Now tap your WORK or specific destination."

        } else if (workMarker == null) {
            // STEP 2: SET WORK (RED)
            workMarker = mMap.addMarker(MarkerOptions()
                .position(latLng)
                .title("Work")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))

            drawCorridor()

            tvTitle.text = "Corridor Defined"
            tvDesc.text = "Check the blue path and confirm to start."
            btnConfirm.visibility = View.VISIBLE
        } else {
            // RESET ON THIRD CLICK
            mMap.clear()
            homeMarker = null
            workMarker = null
            polyline = null
            btnConfirm.visibility = View.GONE
            tvTitle.text = "Set Your Safe Corridor"
            tvDesc.text = "Tap the map to set your HOME location first."
        }
    }

    private fun drawCorridor() {
        val options = PolylineOptions()
            .add(homeMarker!!.position, workMarker!!.position)
            .width(12f)
            .color(Color.parseColor("#3F51B5"))
            .geodesic(true)
        polyline = mMap.addPolyline(options)
    }

    private fun savePathToFirebase() {
        val uid = auth.currentUser?.uid ?: return
        val data = hashMapOf(
            "home_lat" to homeMarker?.position?.latitude,
            "home_lng" to homeMarker?.position?.longitude,
            "work_lat" to workMarker?.position?.latitude,
            "work_lng" to workMarker?.position?.longitude
        )

        db.collection("users").document(uid).set(data, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                // SAVE LOCALLY SO SERVICE CAN READ IT IMMEDIATELY
                val sharedPref = getSharedPreferences("SHE_SHIELD_PREFS", MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putFloat("home_lat", homeMarker?.position?.latitude?.toFloat() ?: 0f)
                    putFloat("home_lng", homeMarker?.position?.longitude?.toFloat() ?: 0f)
                    putFloat("work_lat", workMarker?.position?.latitude?.toFloat() ?: 0f)
                    putFloat("work_lng", workMarker?.position?.longitude?.toFloat() ?: 0f)
                    apply()
                }

                // START THE MONITORING SERVICE WITH API VERSION CHECK
                val intent = Intent(this, SafetyMonitorService::class.java)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }

                Toast.makeText(this, "Safe Corridor Activated!", Toast.LENGTH_LONG).show()
                finish()
            }
    }
} // Class properly closed here