package com.example.ubercloneapp

import android.Manifest
import android.animation.Animator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.icu.lang.UCharacter.getDirection
import android.location.Location
import android.location.LocationListener
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.example.ubercloneapp.common.Common
import com.example.ubercloneapp.remote.IGoogleApi
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.github.glomadrian.materialanimatedswitch.MaterialAnimatedSwitch
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GooglePlayServicesUtil
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.core.SyncTree
import kotlinx.android.synthetic.main.activity_welcome2.*
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.lang.Exception

class Welcome : AppCompatActivity(), OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener{

    private lateinit var mMap: GoogleMap
    private lateinit var mLocationRequest: LocationRequest
    private lateinit var mGoogleApiClient: GoogleApiClient
    private lateinit var mLastLocation: Location

    val UPATE_INTERVAL = 5000
    val FATEST_INTERVAL = 3000
    val DISPLACEMENT = 10

    val MY_PERMISSION_REQUEST_CODE = 7000
    val PLAY_SERVICE_RES_REQUEST = 7001

    lateinit var drivers: DatabaseReference
    lateinit var geoFire: GeoFire

    private var mCurrent: Marker? = null

    //Car animation
    private lateinit var polyLineList: List<LatLng>
    private lateinit var pickupLocationMarker: Marker
    private var v: Float = 0f
    private var lat = 0.0
    private var lng = 0.0
    private lateinit var handler: Handler
    private lateinit var startPosition: LatLng
    private lateinit var endPosition: LatLng
    private lateinit var currentPosition: LatLng
    var index = 0
    var next = 0
    private lateinit var btnGo: Button
    private lateinit var edtPlace: EditText
    private var destination = ""
    private lateinit var polylineOptions: PolylineOptions
    private lateinit var blackPolylineOptions: PolylineOptions
    private lateinit var blackPolyLine: Polyline
    private lateinit var greyPolyLine: Polyline
    private lateinit var carMarker: Marker

    private var mService: IGoogleApi? = null

    var drawPathRunnable = object: Runnable{
        override fun run() {
            if (index < polyLineList.size -1 ) {
                index++
                next = index + 1
            }

            if (index < polyLineList.size - 1) {
                startPosition = polyLineList[index]
                endPosition = polyLineList[next]
            }

            var valueAnimator = ValueAnimator.ofFloat(0f,1f)
            valueAnimator.duration = 3000
            valueAnimator.interpolator = LinearInterpolator()
            valueAnimator.addUpdateListener(object: ValueAnimator.AnimatorUpdateListener{
                override fun onAnimationUpdate(animation: ValueAnimator?) {
                    v = valueAnimator.animatedFraction
                    lng = v * endPosition.longitude + (1-v) * startPosition.longitude
                    lat = v * endPosition.latitude + (1-v) * startPosition.latitude
                    var newPos = LatLng(lat,lng)
                    carMarker.position = newPos
                    carMarker.setAnchor(0.5f, 0.5f)
                    carMarker.rotation = getBearing(startPosition, newPos)
                    mMap.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(newPos).zoom(15.5f).build()))
                }
            })
            valueAnimator.start()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome2)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        //
        polyLineList = ArrayList()
        btnGo.setOnClickListener {
            destination = edtPlace.text.toString()
            destination = destination.replace(" ", "+")
            Log.d("EDMTDEV", destination)
            getDirection()
        }

        //Geo Fire
        drivers = FirebaseDatabase.getInstance().getReference("Drivers")
        geoFire = GeoFire(drivers)
        mService = Common.getGoogleAPI()
        setupLocation()

        locationSwitch.setOnCheckedChangeListener {
            if (it) {
                startLocationUpdate()
                displayLocation()
                Snackbar.make(mapFragment.view!!, "You're online", Snackbar.LENGTH_SHORT).show()
            } else {
                stopLocationUpdate()
                mCurrent?.remove()
                Snackbar.make(mapFragment.view!!, "You're offline", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun getDirection() {
        currentPosition = LatLng(mLastLocation.latitude, mLastLocation.longitude)
        var requestApi: String? = null
        try {
            requestApi = "https://maps.googleapis.com/maps/api/directions/json?"+"mode=driving&"+"transit_routing_preference=less_driving&"+"origin=${currentPosition.latitude},${currentPosition.longitude}&destination=$destination&key=${resources.getString(R.string.google_direction_api)}"
            Log.d("EMDTDEV", requestApi)
            mService?.getPath(requestApi)?.enqueue(object: Callback<String>{
                override fun onFailure(call: Call<String>, t: Throwable) {
                    Toast.makeText(this@Welcome, "ERRR: ${t.message}", Toast.LENGTH_LONG).show()
                }

                override fun onResponse(call: Call<String>, response: Response<String>) {
                    var jsonObject = JSONObject(response.body().toString())
                    var jsonArray = jsonObject.getJSONArray("routes")
                    for (i in 0..jsonArray.length()) {
                        var route = jsonArray.getJSONObject(i)
                        var poly = route.getJSONObject("overview_polyline")
                        var polyline = poly.getString("points")
                        polyLineList = decodePoly(polyline)
                    }
                }
            })
            //Adjusting bounds
            var builder = LatLngBounds.Builder()
            for (latlng in polyLineList) {
                builder.include(latlng)
            }

            var bounds = builder.build()
            var mCameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 2)
            mMap.animateCamera(mCameraUpdate)

            polylineOptions = PolylineOptions()
            polylineOptions.color(Color.GRAY)
            polylineOptions.width(5f)
            polylineOptions.startCap(SquareCap())
            polylineOptions.endCap(SquareCap())
            polylineOptions.jointType(JointType.ROUND)
            polylineOptions.addAll(polyLineList)
            greyPolyLine = mMap.addPolyline(polylineOptions)

            blackPolylineOptions = PolylineOptions()
            blackPolylineOptions.color(Color.BLACK)
            blackPolylineOptions.width(5f)
            blackPolylineOptions.startCap(SquareCap())
            blackPolylineOptions.endCap(SquareCap())
            blackPolylineOptions.jointType(JointType.ROUND)
            blackPolylineOptions.addAll(polyLineList)
            blackPolyLine = mMap.addPolyline(polylineOptions)

            mMap.addMarker(MarkerOptions()
                .position(polyLineList[polyLineList.size-1])
                .title("Pickup Location"))

            //Animation
            var polyLineAnimator = ValueAnimator.ofInt(0,100)
            polyLineAnimator.duration = 2000
            polyLineAnimator.interpolator = LinearInterpolator()
            polyLineAnimator.addUpdateListener(object: ValueAnimator.AnimatorUpdateListener{
                override fun onAnimationUpdate(animation: ValueAnimator?) {
                    var points = greyPolyLine.points
                    var percentValue = animation?.animatedValue as Int
                    var size = points.size
                    var newPoints =  (size * (percentValue/100.0f)).toInt()
                    var p = points.subList(0, newPoints)
                    blackPolyLine.points = p
                }
            })
            polyLineAnimator.start()
            carMarker = mMap.addMarker(MarkerOptions().position(currentPosition).flat(true).icon(BitmapDescriptorFactory.fromResource(R.drawable.car)))

            handler = Handler()
            index = -1
            next = 1
            handler.post(drawPathRunnable, 3000)


        } catch (e: Exception) {

        }
    }

    private fun decodePoly(encoded: String): List<LatLng> {

        val poly = java.util.ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(
                lat.toDouble() / 1E5,
                lng.toDouble() / 1E5
            )
            poly.add(p)
        }

        return poly
    }


    private fun setupLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //Request runtime permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION), MY_PERMISSION_REQUEST_CODE)
        } else {
            if  (checkPlayService()) {
                buildGoogleApiClient()
                createLocationRequest()
                if (locationSwitch.isChecked)
                    displayLocation()
            }
        }
    }

    private fun createLocationRequest() {
        mLocationRequest = LocationRequest()
        mLocationRequest.interval = UPATE_INTERVAL.toLong()
        mLocationRequest.fastestInterval = FATEST_INTERVAL.toLong()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.smallestDisplacement = DISPLACEMENT.toFloat()
    }

    private fun buildGoogleApiClient() {
        mGoogleApiClient = GoogleApiClient.Builder(this)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .addApi(LocationServices.API)
            .build()
        mGoogleApiClient.connect()
    }

    private fun checkPlayService(): Boolean {
        val resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this)
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this, PLAY_SERVICE_RES_REQUEST).show()
            } else {
                Toast.makeText(this, "This device is not supported", Toast.LENGTH_SHORT).show()
                finish()
            }

            return false
        }

        return true
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
                geoFire.setLocation(FirebaseAuth.getInstance().currentUser?.uid, GeoLocation(latitude,longitude), object:GeoFire.CompletionListener{
                    override fun onComplete(key: String?, error: DatabaseError?) {
                        if (mCurrent != null) {
                            mCurrent!!.remove()// Remove already marker
                        }
                        mCurrent = mMap.addMarker(MarkerOptions()
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
                            .position(LatLng(latitude, longitude))
                            .title("You")
                        )

                        //Move camera to this position
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), 15.0f))
                    }
                })
            }
        } else {
            Log.d("Error", "Can not get your Location")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when(requestCode) {
            MY_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if  (checkPlayService()) {
                        buildGoogleApiClient()
                        createLocationRequest()
                        if (locationSwitch.isChecked)
                            displayLocation()
                    }
                }
            }
        }
    }

    private fun rotateMarker(mCurrent: Marker?, i: Int, mMap: GoogleMap) {
        val handler = Handler()
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
        mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
        mMap.isTrafficEnabled = false
        mMap.isIndoorEnabled = false
        mMap.isBuildingsEnabled = false
        mMap.uiSettings.isZoomControlsEnabled = true
    }

    override fun onConnected(p0: Bundle?) {
        displayLocation()
        startLocationUpdate()
    }

    override fun onConnectionSuspended(p0: Int) {
        mGoogleApiClient.connect()
    }

    override fun onConnectionFailed(p0: ConnectionResult) {

    }

    override fun onLocationChanged(location: Location?) {
        mLastLocation = location!!
        displayLocation()
    }
}
