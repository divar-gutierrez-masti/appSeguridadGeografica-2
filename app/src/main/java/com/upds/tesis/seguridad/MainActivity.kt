package com.upds.tesis.seguridad

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.upds.tesis.seguridad.R

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var decryptButton: Button
    private lateinit var encryptButton: Button
    private lateinit var secretDataEditText: EditText
    private lateinit var requestPermissionsButton: Button

    private val aesHelper = AESHelper()
    private var isInsideGeofence = false
    private var encryptedData: String? = null

    private val updsLatitude = -17.382092
    private val updsLongitude = -66.155890
    //kilometros a la redonda
    private val geofenceRadiusMeters = 200f
    private val geofenceId = "TESIS_UPDS_SECURITY_ZONE"

    private val geofencingClient by lazy {
        LocationServices.getGeofencingClient(this)
    }

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(this, GeoreferenceBroadcastReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        PendingIntent.getBroadcast(this, 0, intent, flags)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        decryptButton = findViewById(R.id.decryptButton)
        encryptButton = findViewById(R.id.encryptButton)
        secretDataEditText = findViewById(R.id.secretDataEditText)
        requestPermissionsButton = findViewById(R.id.requestPermissionsButton)

        ContextCompat.registerReceiver(this, geofenceReceiver, IntentFilter("GEOFENCE_STATUS_ACTION"), ContextCompat.RECEIVER_NOT_EXPORTED)

        encryptButton.setOnClickListener { encryptData() }
        decryptButton.setOnClickListener { decryptData() }
        requestPermissionsButton.setOnClickListener { requestLocationPermissions() }
        decryptButton.isEnabled = false
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(geofenceReceiver)
        geofencingClient.removeGeofences(listOf(geofenceId))
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                requestBackgroundLocationPermission()
            }
            else -> {
                Toast.makeText(this, "Permiso de ubicación denegado. La seguridad no funcionará.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            requestBackgroundLocationPermission()
        } else {
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                addGeofence()
            } else {
                locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            }
        } else {
            addGeofence()
        }
    }

    private fun createGeofence(): Geofence {
        return Geofence.Builder()
            .setRequestId(geofenceId)
            .setCircularRegion(updsLatitude, updsLongitude, geofenceRadiusMeters)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT or Geofence.GEOFENCE_TRANSITION_DWELL)
            .setLoiteringDelay(5000)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()
    }

    private fun getGeofencingRequest(): GeofencingRequest {
        return GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(createGeofence())
            .build()
    }

    private fun addGeofence() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        geofencingClient.removeGeofences(listOf(geofenceId))
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Geocerca anterior removida con éxito.")
                }

                geofencingClient.addGeofences(getGeofencingRequest(), geofencePendingIntent)
                    .addOnSuccessListener {
                        Log.i(TAG, "Geocerca de la UPDS registrada con éxito.")
                        Toast.makeText(this, "Geocerca de UPDS Activada.", Toast.LENGTH_SHORT).show()
                        updateGeofenceUI(true, "Monitoreo ACTIVO. Cifrado HABILITADO.")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Fallo al registrar la Geocerca: ${e.message}")
                        Toast.makeText(this, "Fallo en Geofencing: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
    }

    private val geofenceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val isInside = it.getBooleanExtra("GEOFENCE_STATUS", false)
                val message = it.getStringExtra("GEOFENCE_MESSAGE")
                updateGeofenceUI(isInside, message ?: "Estado de Geocerca desconocido")
            }
        }
    }

    private fun updateGeofenceUI(isInside: Boolean, message: String) {
        isInsideGeofence = isInside
        statusTextView.text = message

        if (isInside) {
            statusTextView.setTextColor("#4CAF50".toColorInt())
            decryptButton.isEnabled = true
        } else {
            statusTextView.setTextColor("#F44336".toColorInt())
            decryptButton.isEnabled = false

            if (encryptedData != null) {
                secretDataEditText.setText(encryptedData)
            }
        }
    }

    private fun encryptData() {
        val plainText = secretDataEditText.text.toString()
        if (plainText.isEmpty()) {
            Toast.makeText(this, "Ingrese texto secreto para cifrar.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            encryptedData = aesHelper.encrypt(plainText)
            secretDataEditText.setText(encryptedData)
            Toast.makeText(this, "¡Cifrado AES-256 exitoso!", Toast.LENGTH_SHORT).show()
            encryptButton.visibility = View.GONE
            decryptButton.visibility = View.VISIBLE

        } catch (e: Exception) {
            Log.e(TAG, "Error en Cifrado: ${e.message}")
            Toast.makeText(this, "Error de cifrado. Ver logs.", Toast.LENGTH_LONG).show()
        }
    }

    private fun decryptData() {
        if (!isInsideGeofence) {
            Toast.makeText(this, "ACCESO DENEGADO: Debe estar dentro de la Geocerca UPDS para descifrar.", Toast.LENGTH_LONG).show()
            return
        }

        val cipherText = secretDataEditText.text.toString()

        try {
            val decryptedText = aesHelper.decrypt(cipherText)
            secretDataEditText.setText(decryptedText)
            encryptedData = null
            Toast.makeText(this, "¡Descifrado exitoso! Datos recuperados.", Toast.LENGTH_SHORT).show()
            encryptButton.visibility = View.VISIBLE
            decryptButton.visibility = View.GONE

        } catch (e: Exception) {
            Log.e(TAG, "Error en Descifrado: ${e.message}")
            Toast.makeText(this, "Error de descifrado. Datos corruptos o clave incorrecta.", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
