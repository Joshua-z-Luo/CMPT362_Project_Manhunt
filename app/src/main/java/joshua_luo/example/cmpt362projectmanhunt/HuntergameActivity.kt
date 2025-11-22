package joshua_luo.example.cmpt362projectmanhunt

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class HunterGameActivity : FragmentActivity(), OnMapReadyCallback {

    private val client by lazy { OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build() }
    private lateinit var fused: FusedLocationProviderClient
    private var locCallback: LocationCallback? = null
    private var pollJob: Job? = null
    private var gameTimer: CountDownTimer? = null
    private var googleMap: GoogleMap? = null
    private var hunterMarker: Marker? = null
    private var hunterCircle: Circle? = null
    private val runnerMarkers = mutableMapOf<String, Marker>()
    private lateinit var tvGameTimer: TextView
    private var userId: String? = null
    private var token: String? = null
    private var roomCode: String? = null
    private var baseUrl: String? = null
    private var timerMinutes: Int = 30
    private var hunterRange: Int = 50
    private var abilityMode: Boolean = false

    private var hunterLat: Double = 0.0
    private var hunterLon: Double = 0.0

    private val permReq = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_huntergame )

        fused = LocationServices.getFusedLocationProviderClient(this)
        tvGameTimer = findViewById(R.id.tvGameTimer)

        userId = intent.getStringExtra("userId")
        roomCode = intent.getStringExtra("roomCode")
        baseUrl = intent.getStringExtra("baseUrl")
        timerMinutes = intent.getIntExtra("timerMinutes", 30 )
        hunterRange = intent.getIntExtra("hunterRange", 50)
        abilityMode = intent.getBooleanExtra("abilityMode", false)


        val prefs = getSharedPreferences("GameData", MODE_PRIVATE)
        token = prefs.getString("token", null)

        permReq.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        startGameTimer()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        try {
            googleMap?.isMyLocationEnabled = true
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        startLocationTracking()
        startPollingRunners()
    }

    private fun startGameTimer() {
        val totalMillis = timerMinutes * 60 * 1000L
        gameTimer = object : CountDownTimer(totalMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) /60
                val seconds = (millisUntilFinished / 1000) %60
                tvGameTimer.text = "${minutes}min ${seconds}sec"
            }

            override fun onFinish() {
                tvGameTimer.text = "0min 0sec"
                Toast.makeText(this@HunterGameActivity , "Time's up! Runners win!", Toast.LENGTH_LONG).show()
                // TODO: when game ends
                finish()
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        val uid = userId ?: return
        val tok = token ?: return
        val base = baseUrl ?: return
        val code = roomCode ?: return

        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                hunterLat = loc.latitude
                hunterLon = loc.longitude

                updateHunterMarker(hunterLat, hunterLon)

                lifecycleScope.launch(Dispatchers.IO ) {
                    try {
                        val body = JSONObject().apply {
                            put("userId", uid)
                            put("token", tok)
                            put("lat", hunterLat)
                            put("lon", hunterLon)
                        }.toString()
                        val r = Request.Builder()
                            .url("$base/rooms/$code/loc")
                            .post(body.toRequestBody("application/json".toMediaType()))
                            .build()
                        client.newCall(r).execute().close()
                    } catch (_: Exception) { }
                }
            }
        }

        locCallback = cb
        fused.requestLocationUpdates(req, cb, mainLooper)
    }

    private fun updateHunterMarker(lat: Double, lon: Double) {
        val position = LatLng(lat, lon)

        if (hunterMarker == null) {
            hunterMarker = googleMap?.addMarker(
                MarkerOptions()
                    .position(position)
                    .title("You (Hunter)")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )

            hunterCircle = googleMap?.addCircle(
                CircleOptions()
                    .center(position)
                    .radius(hunterRange.toDouble())
                    .strokeColor(Color.RED )
                    .strokeWidth(3f)
                    .fillColor(Color.argb(50, 255, 0, 0))
            )


            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
        } else {
            hunterMarker?.position = position
            hunterCircle?.center = position
        }
    }

    private fun startPollingRunners() {
        val base = baseUrl ?: return
        val code = roomCode ?: return

        pollJob?.cancel()
        pollJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val r = Request.Builder().url("$base/rooms/$code/state").get().build()
                    client.newCall(r).execute().use { resp ->
                        val txt = resp.body?.string().orEmpty()
                        if (resp.isSuccessful) {
                            val obj = JSONObject(txt)
                            val arr = obj.optJSONArray("members") ?: JSONArray()

                            withContext(Dispatchers.Main) {
                                updateRunnerMarkers(arr)
                            }
                        }
                    }
                } catch (_: Exception) { }
                delay(3000L)
            }
        }
    }

    private fun updateRunnerMarkers(membersArray: JSONArray) {
        val currentRunnerIds = mutableSetOf<String>()

        for (i in 0 until membersArray.length()) {
            val m = membersArray.getJSONObject(i)
            val id = m.optString("userId")

            if (id == userId) continue

            val locObj = m.optJSONObject("loc") ?: continue
            val lat = locObj.optDouble("lat")
            val lon = locObj.optDouble("lon")

            if (lat == 0.0 &&lon == 0.0) continue

            currentRunnerIds.add(id)

            val distance = calculateDistance(hunterLat, hunterLon, lat, lon)

            if (distance <= hunterRange) {
                val position = LatLng(lat, lon)
                val name = m.optString("name", "").ifBlank { id }

                if (runnerMarkers.containsKey(id)) {
                    runnerMarkers[id]?.position = position
                } else {
                    val marker = googleMap?.addMarker(
                        MarkerOptions()
                            .position(position)
                            .title("Runner: $name")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE  ))
                    )
                    if (marker != null) {
                        runnerMarkers[id] = marker
                    }
                }
            } else {
                runnerMarkers[id]?.remove()
                runnerMarkers.remove(id)
            }
        }

        val toRemove = runnerMarkers.keys - currentRunnerIds
        toRemove.forEach {
            runnerMarkers[it]?.remove()
            runnerMarkers.remove(it)
        }
    }
    // reference https://stackoverflow.com/questions/3694380/calculating-distance-between-two-points-using-latitude-longitude
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a ))
        return R * c
    }

    override fun onDestroy() {
        super.onDestroy()
        locCallback?.let { fused.removeLocationUpdates(it) }
        pollJob?.cancel()
        gameTimer?.cancel()
    }
}