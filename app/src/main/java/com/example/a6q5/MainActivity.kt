package com.example.a6q5

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.a6q5.ui.theme.A6Q5Theme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

class MainActivity : ComponentActivity() {

    private val permissionGranted = mutableStateOf(false)
    private val myLocation = mutableStateOf<LatLng?>(null)
    private val addressText = mutableStateOf("Need location permission")
    private val userMarkers = mutableStateListOf<LatLng>()

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted.value = granted
        if (granted) {
            loadLocation()
        } else {
            addressText.value = "Permission is required"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askForPermission()
        setContent {
            A6Q5Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LocationScreen(
                        hasPermission = permissionGranted.value,
                        myLocation = myLocation.value,
                        address = addressText.value,
                        markers = userMarkers,
                        onRequestPermission = { askForPermission() },
                        onRefreshLocation = { loadLocation() },
                        onMapClick = { latLng ->
                            userMarkers.add(latLng)
                            addressText.value = "Marker at ${latLng.latitude}, ${latLng.longitude}"
                        }
                    )
                }
            }
        }
    }

    // Asks the user for location permission
    private fun askForPermission() {
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(this, fine) == PackageManager.PERMISSION_GRANTED) {
            permissionGranted.value = true
            loadLocation()
        } else {
            permissionLauncher.launch(fine)
        }
    }

    // Pulls the last known location and tries to reverse geocode it
    private fun loadLocation() {
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
        val hasPerm =
            ContextCompat.checkSelfPermission(this, fine) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, coarse) == PackageManager.PERMISSION_GRANTED

        if (!hasPerm) {
            addressText.value = "Give permission to load location"
            return
        }

        fusedClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val spot = LatLng(location.latitude, location.longitude)
                myLocation.value = spot
                addressText.value = reverseGeocode(spot)
            } else {
                addressText.value = "No last known location yet"
            }
        }.addOnFailureListener {
            addressText.value = "Could not get location: ${it.message}"
        }
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(latLng: LatLng): String {
        return try {
            val geo = Geocoder(this)
            val result = geo.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (!result.isNullOrEmpty()) {
                val addr = result[0]
                listOfNotNull(
                    addr.thoroughfare,
                    addr.locality,
                    addr.adminArea,
                    addr.postalCode
                ).joinToString(", ").ifBlank { "Address not found" }
            } else {
                "Address not found"
            }
        } catch (e: Exception) {
            "Geocoder error: ${e.message}"
        }
    }
}

@Composable
fun LocationScreen(
    hasPermission: Boolean,
    myLocation: LatLng?,
    address: String,
    markers: List<LatLng>,
    onRequestPermission: () -> Unit,
    onRefreshLocation: () -> Unit,
    onMapClick: (LatLng) -> Unit
) {
    val defaultSpot = remember { LatLng(37.422, -122.084) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(myLocation ?: defaultSpot, 14f)
    }

    LaunchedEffect(myLocation) {
        myLocation?.let {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 16f))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Simple Maps Demo", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = address,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = hasPermission && myLocation != null
                ),
                uiSettings = MapUiSettings(zoomControlsEnabled = true),
                onMapClick = { onMapClick(it) }
            ) {
                myLocation?.let {
                    Marker(
                        state = MarkerState(it),
                        title = "You are here"
                    )
                }
                markers.forEachIndexed { index, latLng ->
                    Marker(
                        state = MarkerState(latLng),
                        title = "Marker ${index + 1}"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(text = if (hasPermission) "Tap map to drop markers." else "Need location permission.")
        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = { if (hasPermission) onRefreshLocation() else onRequestPermission() },
            modifier = Modifier.size(width = 200.dp, height = 44.dp)
        ) {
            Text(text = if (hasPermission) "Refresh My Location" else "Request Permission")
        }
    }
}
