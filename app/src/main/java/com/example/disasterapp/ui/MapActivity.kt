package com.example.disasterapp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.disasterapp.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

/**
 * MapActivity
 * - Initializes Google Map
 * - Shows user's location (if permission granted)
 * - Adds sample evacuation shelter markers (static list; replace with real data source)
 */
class MapActivity : AppCompatActivity(), OnMapReadyCallback {
    companion object {
        private const val TAG = "MapActivity"
        private const val REQUEST_LOCATION = 101
    }

    private var map: GoogleMap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map?.uiSettings?.isZoomControlsEnabled = true

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableLocation()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION)
        }

        addSampleShelters()
    }

    private fun enableLocation() {
        try {
            map?.isMyLocationEnabled = true
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission missing: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableLocation()
            }
        }
    }

    private fun addSampleShelters() {
        val shelters = listOf(
            Shelter("市民ホール避難所", LatLng(35.6895, 139.6917)), // Tokyo sample
            Shelter("小学校避難所", LatLng(35.695, 139.700)),
            Shelter("公民館避難所", LatLng(35.682, 139.765))
        )

        shelters.forEach { s ->
            map?.addMarker(MarkerOptions().position(s.latlng).title(s.name))
        }

        // Move camera to first shelter
        if (shelters.isNotEmpty()) {
            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(shelters.first().latlng, 13f))
        }
    }

    data class Shelter(val name: String, val latlng: LatLng)
}
