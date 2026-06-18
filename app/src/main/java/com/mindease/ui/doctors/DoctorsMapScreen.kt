package com.mindease.ui.doctors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import com.mindease.BuildConfig

data class DoctorInfo(
    val name: String,
    val address: String,
    val latLng: LatLng
)

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DoctorsMapScreen(navController: NavController) {

    val permissionState =
        rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    LaunchedEffect(Unit) {
        if (!permissionState.status.isGranted) {
            permissionState.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nearby Doctors") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->

        if (permissionState.status.isGranted) {
            MapContent(padding)
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = { permissionState.launchPermissionRequest() }) {
                    Text("Grant Location Permission")
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun MapContent(padding: PaddingValues) {

    val context = LocalContext.current

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var doctors by remember { mutableStateOf<List<DoctorInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("") }
    var showFallbackButton by remember { mutableStateOf(false) }

    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(20.5937, 78.9629),
            5f
        )
    }

    LaunchedEffect(Unit) {
        try {
            val location = fusedLocationClient.lastLocation.await()

            if (location != null) {
                val latLng = LatLng(location.latitude, location.longitude)
                userLocation = latLng

                cameraState.animate(
                    update = CameraUpdateFactory.newLatLngZoom(latLng, 13f)
                )

                val result = fetchAllNearbyDoctors(latLng)
                doctors = result.first
                statusMessage = result.second
                showFallbackButton = doctors.isEmpty()
            } else {
                statusMessage = "Could not get your location. Please enable GPS and try again."
                showFallbackButton = true
            }

        } catch (e: Exception) {
            Log.e("MAP", "Error: ${e.message}")
            statusMessage = "Location error: ${e.message}"
            showFallbackButton = true
        } finally {
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(isMyLocationEnabled = true)
        ) {
            userLocation?.let {
                Marker(
                    state = MarkerState(it),
                    title = "You",
                    snippet = "Your current location",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }

            doctors.forEach { doctor ->
                Marker(
                    state = MarkerState(doctor.latLng),
                    title = doctor.name,
                    snippet = doctor.address
                )
            }
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // Status card + Fallback button
        if (!isLoading) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (statusMessage.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (doctors.isNotEmpty())
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            statusMessage,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            color = if (doctors.isNotEmpty())
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // Fallback: Open Google Maps directly
                if (showFallbackButton) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val loc = userLocation
                            val query = "Psychiatrist+Psychologist+Neurologist+Cardiologist"
                            val uri = if (loc != null) {
                                Uri.parse("https://www.google.com/maps/search/$query/@${loc.latitude},${loc.longitude},14z")
                            } else {
                                Uri.parse("https://www.google.com/maps/search/$query")
                            }
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("🔍 Search on Google Maps")
                    }
                }
            }
        }
    }
}

/**
 * Main search function — tries multiple strategies.
 * Returns (list of doctors, status message)
 */
suspend fun fetchAllNearbyDoctors(location: LatLng): Pair<List<DoctorInfo>, String> {

    return withContext(Dispatchers.IO) {

        val apiKey = BuildConfig.MAPS_API_KEY

        if (apiKey.isBlank()) {
            return@withContext Pair(
                emptyList(),
                "Maps API key is not configured. Use 'Search on Google Maps' below."
            )
        }

        // Track if we got REQUEST_DENIED (configuration issue vs no results)
        var lastDeniedMessage = ""

        // ─── Strategy 1: Text Search for mental health professionals ───
        val textSearchQueries = listOf(
            "psychiatrist near me",
            "psychologist near me",
            "mental health clinic",
            "therapist counselor"
        )

        val allDoctors = mutableListOf<DoctorInfo>()

        for (query in textSearchQueries) {
            val result = textSearchPlaces(location, query, apiKey)
            if (result.first.isNotEmpty()) {
                allDoctors.addAll(result.first)
            }
            if (result.second.isNotEmpty()) lastDeniedMessage = result.second
        }

        val uniqueSpecialists = deduplicateDoctors(allDoctors)

        if (uniqueSpecialists.isNotEmpty()) {
            return@withContext Pair(
                uniqueSpecialists,
                "${uniqueSpecialists.size} mental health specialists found nearby"
            )
        }

        // ─── Strategy 2: Nearby Search with type=doctor ───
        Log.d("PLACES", "No specialists found, trying general doctors...")

        val generalResult = nearbySearchPlaces(location, apiKey, type = "doctor", radius = 10000)
        if (generalResult.second.isNotEmpty()) lastDeniedMessage = generalResult.second

        if (generalResult.first.isNotEmpty()) {
            return@withContext Pair(
                generalResult.first,
                "${generalResult.first.size} doctors found nearby"
            )
        }

        // ─── Strategy 3: Nearby Search with type=hospital ───
        val hospitalResult = nearbySearchPlaces(location, apiKey, type = "hospital", radius = 15000)
        if (hospitalResult.second.isNotEmpty()) lastDeniedMessage = hospitalResult.second

        if (hospitalResult.first.isNotEmpty()) {
            return@withContext Pair(
                hospitalResult.first,
                "${hospitalResult.first.size} hospitals found nearby"
            )
        }

        // ─── All API strategies failed — show helpful error ───
        Log.e("PLACES", "No results from any API search strategy")

        val errorMessage = if (lastDeniedMessage.isNotEmpty()) {
            "API key not authorized. Go to Google Cloud Console → Enable 'Places API' and remove API key restrictions."
        } else {
            "No doctors found nearby. Tap below to search on Google Maps."
        }

        Pair(emptyList(), errorMessage)
    }
}

private fun textSearchPlaces(
    location: LatLng,
    query: String,
    apiKey: String
): Pair<List<DoctorInfo>, String> {
    return try {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        val url = buildString {
            append("https://maps.googleapis.com/maps/api/place/textsearch/json")
            append("?query=$encodedQuery")
            append("&location=${location.latitude},${location.longitude}")
            append("&radius=10000")
            append("&key=$apiKey")
        }

        Log.d("PLACES", "Text search: query='$query'")

        val response = URL(url).readText()
        val json = JSONObject(response)

        val status = json.optString("status")
        val errorMsg = json.optString("error_message", "")
        Log.d("PLACES", "Text search status for '$query': $status ${if (errorMsg.isNotEmpty()) "($errorMsg)" else ""}")

        if (status == "REQUEST_DENIED") {
            Log.e("PLACES", "API DENIED: $errorMsg")
            return Pair(emptyList(), errorMsg)
        }

        if (status == "ZERO_RESULTS") {
            Log.d("PLACES", "No results for '$query' at this location")
            return Pair(emptyList(), "")
        }

        if (status != "OK") {
            Log.e("PLACES", "Unexpected status '$status' for '$query': $errorMsg")
            return Pair(emptyList(), "")
        }

        Pair(parseResults(json), "")

    } catch (e: Exception) {
        Log.e("PLACES", "Text search error for '$query': ${e.message}")
        Pair(emptyList(), "")
    }
}

private fun nearbySearchPlaces(
    location: LatLng,
    apiKey: String,
    type: String = "doctor",
    radius: Int = 10000
): Pair<List<DoctorInfo>, String> {
    return try {
        val url = buildString {
            append("https://maps.googleapis.com/maps/api/place/nearbysearch/json")
            append("?location=${location.latitude},${location.longitude}")
            append("&radius=$radius")
            append("&type=$type")
            append("&key=$apiKey")
        }

        Log.d("PLACES", "Nearby search: type=$type, radius=$radius")

        val response = URL(url).readText()
        val json = JSONObject(response)

        val status = json.optString("status")
        val errorMsg = json.optString("error_message", "")
        Log.d("PLACES", "Nearby search status for '$type': $status ${if (errorMsg.isNotEmpty()) "($errorMsg)" else ""}")

        if (status == "REQUEST_DENIED") {
            Log.e("PLACES", "API DENIED: $errorMsg")
            return Pair(emptyList(), errorMsg)
        }

        if (status == "ZERO_RESULTS") {
            Log.d("PLACES", "No results for type '$type' at this location")
            return Pair(emptyList(), "")
        }

        if (status != "OK") {
            Log.e("PLACES", "Unexpected status '$status' for '$type': $errorMsg")
            return Pair(emptyList(), "")
        }

        Pair(parseResults(json), "")

    } catch (e: Exception) {
        Log.e("PLACES", "Nearby search error: ${e.message}")
        Pair(emptyList(), "")
    }
}

private fun parseResults(json: JSONObject): List<DoctorInfo> {
    val results = json.getJSONArray("results")
    val list = mutableListOf<DoctorInfo>()

    for (i in 0 until results.length()) {
        val obj = results.getJSONObject(i)
        val loc = obj.getJSONObject("geometry").getJSONObject("location")

        val name = obj.optString("name", "Doctor")
        val address = obj.optString("formatted_address",
            obj.optString("vicinity", ""))

        list.add(
            DoctorInfo(
                name = name,
                address = address,
                latLng = LatLng(loc.getDouble("lat"), loc.getDouble("lng"))
            )
        )
    }

    return list
}

private fun deduplicateDoctors(doctors: List<DoctorInfo>): List<DoctorInfo> {
    if (doctors.isEmpty()) return doctors

    val unique = mutableListOf<DoctorInfo>()

    for (doctor in doctors) {
        val isDuplicate = unique.any { existing ->
            val latDiff = Math.abs(existing.latLng.latitude - doctor.latLng.latitude)
            val lngDiff = Math.abs(existing.latLng.longitude - doctor.latLng.longitude)
            latDiff < 0.001 && lngDiff < 0.001
        }

        if (!isDuplicate) {
            unique.add(doctor)
        }
    }

    return unique
}