package com.example.ubercloneapp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.view.animation.LinearInterpolator
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.github.glomadrian.materialanimatedswitch.MaterialAnimatedSwitch
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import kotlinx.android.synthetic.main.activity_welcome2.*

class Welcome : AppCompatActivity(), OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener{

    private lateinit var mMap: GoogleMap
    private lateinit var mLocationRequest: LocationRequest
    private lateinit var mGoogleApiClient: GoogleApiClient
    private lateinit var mLastLocation: Location

    val UPATE_INTERVAL = 5000
    val FATEST_INTERVAL = 3000
    val DISPLACEMENT = 10

    lateinit var drivers: DatabaseReference
    lateinit var geoFire: GeoFire

     private var mCurrent: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome2)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        locationSwitch.setOnCheckedChangeListener {
            if (it) {
                startLocationUpdate()
                displayLocation()
                Snackbar.make(mapFragment.view!!, "You're online", Snackbar.LENGTH_SHORT).show()
            } else {
                stopLocationUpdate()
                Snackbar.make(mapFragment.view!!, "You're offline", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient)
        if (mLastLocation != null) {
            if (locationSwitch.isChecked) {
                val latitude = mLastLocation.latitude
                val longitude = mLastLocation.longitude

                //update to Firebase
                geoFire.setLocation(FirebaseAuth.getInstance().currentUser?.uid, GeoLocation(latitude,longitude), GeoFire.CompletionListener { key, error ->
                    if (mCurrent != null) {
                        mCurrent.remove()// Remove already marker
                        mCurrent = mMap.addMarker(MarkerOptions()
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
                            .position(LatLng(latitude, longitude))
                            .title("You")
                        )

                        //Move camera to this position
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), 15.0f))

                        //Draw animation rotate marker
                        rotateMarker(mCurrent, -360, mMap)
                    }
                })
            }
        }
    }

    private fun rotateMarker(mCurrent: Marker?, i: Int, mMap: GoogleMap) {
        var handler = Handler()
        val start = SystemClock.uptimeMillis()
        val startRotation = mCurrent?.rotation

        val duration = 1500

        val interpolator = LinearInterpolator()

        handler.post(object: Runnable{
            override fun run() {
                val elapsed = SystemClock.uptimeMillis() - start
                val t = interpolator.getInterpolation((elapsed/duration).toFloat())
                val rot = t * i + (1 - t) * startRotation!!
                if (- rot > 180) {
                    mCurrent.rotation = rot/2
                } else {
                    mCurrent.rotation = rot
                }

                if (t <1.0) {
                    handler.postDelayed(this, 16)
                }
            }
        })
    }

    private fun startLocationUpdate() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this)
    }

    private fun stopLocationUpdate() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this)
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Add a marker in Sydney and move the camera
        val sydney = LatLng(-34.0, 151.0)
        mMap.addMarker(MarkerOptions().position(sydney).title("Marker in Sydney"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))
    }

    override fun onConnected(p0: Bundle?) {
        displayLocation()
        startLocationUpdate()
    }

    override fun onConnectionSuspended(p0: Int) {
        mGoogleApiClient.connect()
    }

    override fun onConnectionFailed(p0: ConnectionResult) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onLocationChanged(location: Location?) {
        mLastLocation = location!!

    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onProviderEnabled(provider: String?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onProviderDisabled(provider: String?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
