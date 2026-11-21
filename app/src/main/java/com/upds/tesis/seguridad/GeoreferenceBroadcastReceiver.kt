package com.upds.tesis.seguridad

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent

class GeoreferenceBroadcastReceiver : BroadcastReceiver() {

    private val TAG = "GeofenceReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent == null) {
            Log.e(TAG, "GeofencingEvent es nulo. El intent no es de Geofencing.")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorCode = geofencingEvent.errorCode
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(errorCode)
            Log.e(TAG, "Error en el evento de Geofencing: $errorMessage (Code: $errorCode)")
            sendMessageToActivity(context, false, "Error en Geocerca: $errorMessage")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        val geofenceList = geofencingEvent.triggeringGeofences

        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
            geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT ||
            geofenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL) {

            val geofenceName = geofenceList?.firstOrNull()?.requestId ?: "Geocerca Desconocida"
            val isInside = geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
                    geofenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL

            val statusMessage = when (geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> "¡ACCESO HABILITADO! DENTRO de la UPDS: $geofenceName"
                Geofence.GEOFENCE_TRANSITION_EXIT -> "¡ACCESO BLOQUEADO! FUERA de la UPDS: $geofenceName"
                Geofence.GEOFENCE_TRANSITION_DWELL -> "PERMANENCIA DENTRO: $geofenceName"
                else -> "Estado de Geocerca Desconocido."
            }

            Log.i(TAG, statusMessage)
            sendMessageToActivity(context, isInside, statusMessage)

        } else {
            Log.e(TAG, "Tipo de Transición Inválido: $geofenceTransition")
        }
    }

    private fun sendMessageToActivity(context: Context, isInside: Boolean, message: String) {
        val intent = Intent("GEOFENCE_STATUS_ACTION").apply {
            putExtra("GEOFENCE_STATUS", isInside)
            putExtra("GEOFENCE_MESSAGE", message)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}
