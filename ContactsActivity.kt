package com.example.she_shield

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ContactsActivity : AppCompatActivity() {

    private lateinit var tvContactName: TextView
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        // 1. LINK UI ELEMENTS
        tvContactName = findViewById(R.id.tvContactName)
        val btnPickContact = findViewById<Button>(R.id.btnPickContact)
        val etManualName = findViewById<EditText>(R.id.etManualName)
        val etManualNumber = findViewById<EditText>(R.id.etManualNumber)
        val btnSaveManual = findViewById<Button>(R.id.btnSaveManual)
        val fabCallGuardian = findViewById<FloatingActionButton>(R.id.fabCallGuardian)

        // 2. CONTACT PICKER LOGIC
        val contactLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                fetchContactDetails(result.data?.data)
            }
        }

        btnPickContact.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            contactLauncher.launch(intent)
        }

        // 3. MANUAL SAVE LOGIC
        btnSaveManual.setOnClickListener {
            val name = etManualName.text.toString().trim()
            val number = etManualNumber.text.toString().trim()

            if (name.isNotEmpty() && number.isNotEmpty()) {
                saveGuardianData(name, number)
                etManualName.text.clear()
                etManualNumber.text.clear()
            } else {
                Toast.makeText(this, "Please enter both name and number", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. QUICK DIAL LOGIC
        fabCallGuardian.setOnClickListener {
            val sharedPref = getSharedPreferences("SHE_SHIELD_PREFS", MODE_PRIVATE)
            val savedNumber = sharedPref.getString("emergency_number", null)

            if (savedNumber != null) {
                val dialIntent = Intent(Intent.ACTION_DIAL)
                dialIntent.data = Uri.parse("tel:$savedNumber")
                startActivity(dialIntent)
            } else {
                Toast.makeText(this, "No guardian saved to call!", Toast.LENGTH_SHORT).show()
            }
        }

        // Load existing data on start
        loadExistingGuardian()
    }

    private fun fetchContactDetails(uri: Uri?) {
        uri?.let {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            contentResolver.query(it, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val number = cursor.getString(0)
                    val name = cursor.getString(1)
                    saveGuardianData(name, number)
                }
            }
        }
    }

    private fun saveGuardianData(name: String, number: String) {
        // A. SAVE LOCALLY (For Offline SOS/Dialing)
        val sharedPref = getSharedPreferences("SHE_SHIELD_PREFS", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("emergency_number", number)
            putString("emergency_name", name)
            apply()
        }

        // B. SAVE TO CLOUD (For Data Backup)
        val uid = auth.currentUser?.uid ?: return
        val update = hashMapOf("contact_name" to name, "contact_number" to number)

        db.collection("users").document(uid).update(update as Map<String, Any>)
            .addOnSuccessListener {
                tvContactName.text = "Guardian: $name ($number)"
                Toast.makeText(this, "Guardian Updated Successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                // If document doesn't exist yet, use set instead of update
                db.collection("users").document(uid).set(update)
                    .addOnSuccessListener {
                        tvContactName.text = "Guardian: $name ($number)"
                    }
            }
    }

    private fun loadExistingGuardian() {
        val sharedPref = getSharedPreferences("SHE_SHIELD_PREFS", MODE_PRIVATE)
        val name = sharedPref.getString("emergency_name", null)
        val number = sharedPref.getString("emergency_number", null)

        if (name != null && number != null) {
            tvContactName.text = "Guardian: $name ($number)"
        }
    }
}