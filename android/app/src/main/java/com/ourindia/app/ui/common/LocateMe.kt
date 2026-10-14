@file:OptIn(ExperimentalMaterial3Api::class)

package com.ourindia.app.ui.common

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.ourindia.app.data.geo.LocalGeographicResolver
import com.ourindia.app.data.geo.ResolvedLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Location ─────────────────────────────────────────────────────────────────

/** Permission → services check → one fix → resolve. Returns the click handler. */
@Composable
fun rememberLocateMe(
    resolver: LocalGeographicResolver,
    onStart: () -> Unit,
    onResolved: (ResolvedLocation) -> Unit,
    onFailed: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showServicesDialog by remember { mutableStateOf(false) }

    val start = {
        if (!locationServicesOn(context)) {
            showServicesDialog = true
        } else {
            onStart()
            requestFix(context, scope, resolver, onResolved, onFailed)
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) start() else showPermissionDialog = true
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Location permission needed") },
            text = { Text("Allow location access to see the party posts that cover where you are.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        )
                    }
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { showPermissionDialog = false }) { Text("Not now") } }
        )
    }
    if (showServicesDialog) {
        AlertDialog(
            onDismissRequest = { showServicesDialog = false },
            title = { Text("Location is off") },
            text = { Text("Turn on location services to find the party structure around you.") },
            confirmButton = {
                TextButton(onClick = {
                    showServicesDialog = false
                    runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                }) { Text("Turn on") }
            },
            dismissButton = { TextButton(onClick = { showServicesDialog = false }) { Text("Not now") } }
        )
    }

    return {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) start()
        else launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
}

fun locationServicesOn(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

@SuppressLint("MissingPermission")
fun requestFix(
    context: Context,
    scope: CoroutineScope,
    resolver: LocalGeographicResolver,
    onResolved: (ResolvedLocation) -> Unit,
    onFailed: (String) -> Unit
) {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    fun deliver(location: Location) {
        scope.launch {
            val resolved = resolver.resolveLocation(location.latitude, location.longitude)
                ?.copy(accuracyMeters = if (location.hasAccuracy()) location.accuracy else null)
            if (resolved != null) onResolved(resolved) else onFailed("Your location is outside India's mapped boundaries")
        }
    }

    try {
        val last = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        // A cached fix is fine if recent; a stale one could be another city.
        if (last != null && System.currentTimeMillis() - last.time < 10 * 60_000) {
            deliver(last)
            return
        }

        val provider = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        var done = false
        // All four callbacks are overridden on purpose: they are only default methods from
        // API 30, and on 26–29 the platform calls them and would hit an AbstractMethodError.
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (done) return
                done = true
                deliver(location)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        @Suppress("DEPRECATION")
        lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        // Without a timeout the spinner ran forever indoors where GPS never fixes.
        scope.launch {
            delay(15_000)
            if (done) return@launch
            done = true
            lm.removeUpdates(listener)
            if (last != null) deliver(last) else onFailed("Couldn't get a location fix — try again with Wi-Fi on")
        }
    } catch (e: SecurityException) {
        onFailed("Location permission was revoked")
    } catch (e: Exception) {
        onFailed("Location is unavailable right now")
    }
}
