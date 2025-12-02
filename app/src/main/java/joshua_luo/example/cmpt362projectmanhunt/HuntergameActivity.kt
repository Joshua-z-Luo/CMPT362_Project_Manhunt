package joshua_luo.example.cmpt362projectmanhunt

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Window
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.card.MaterialCardView
import joshua_luo.example.cmpt362projectmanhunt.model.PowerupTypes
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
    private lateinit var abilityButton: Button

    private var userId: String? = null
    private var token: String? = null
    private var roomCode: String? = null
    private var baseUrl: String? = null
    private var timerMinutes: Int = 30
    private var hunterRange: Int = 50
    private var abilityMode: Boolean = false

    private var hunterLat: Double = 0.0
    private var hunterLon: Double = 0.0

    private lateinit var hunterViewModel: HunterViewModel

    private var selectedAbility: PowerupTypes? = null

    private val permReq = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_huntergame)

        fused = LocationServices.getFusedLocationProviderClient(this)
        tvGameTimer = findViewById(R.id.tvGameTimer)
        abilityButton = findViewById(R.id.abilityButton)

        hunterViewModel = ViewModelProvider(this)[HunterViewModel::class.java]

        userId = intent.getStringExtra("userId")
        roomCode = intent.getStringExtra("roomCode")
        baseUrl = intent.getStringExtra("baseUrl")
        timerMinutes = intent.getIntExtra("timerMinutes", 30)
        hunterRange = intent.getIntExtra("hunterRange", 50)
        abilityMode = intent.getBooleanExtra("abilityMode", false)


        val prefs = getSharedPreferences("GameData", MODE_PRIVATE)
        token = prefs.getString("token", null)

        permReq.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        abilityButton.setOnClickListener {
            if (!abilityMode) {
                Toast.makeText(this, "Abilities are disabled for this match.", Toast.LENGTH_SHORT).show()
            } else {
                showAbilityDialog()
            }
        }

        hunterViewModel.scanRangeMultiplier.observe(this) {
            updateHunterCircleRadius()
        }

        hunterViewModel.trackedRunnerId.observe(this) {
            // appearance handled in updateRunnerMarkers
        }

        hunterViewModel.revealActive.observe(this) {
            // we simply re-run visibility when next poll comes in
        }

        startGameTimer()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        try {
            googleMap?.isMyLocationEnabled = true
        } catch (_: SecurityException) { }
        startLocationTracking()
        startPollingRunners()
    }

    private fun startGameTimer() {
        val totalMillis = timerMinutes * 60 * 1000L
        gameTimer = object : CountDownTimer(totalMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                tvGameTimer.text = "${minutes}min ${seconds}sec"
            }

            override fun onFinish() {
                tvGameTimer.text = "0min 0sec"
                Toast.makeText(this@HunterGameActivity, "Time's up! Runners win!", Toast.LENGTH_LONG).show()

                // Redirect to Game End Summary page
                val intent = Intent(this@HunterGameActivity, GameEndActivity::class.java)
                intent.putExtra("finalTime", "0min 0sec")
                intent.putExtra("isHunter", true)
                startActivity(intent)
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

                val pos = LatLng(hunterLat, hunterLon)
                hunterViewModel.updateHunterPosition(pos)
                updateHunterMarker(hunterLat, hunterLon)

                lifecycleScope.launch(Dispatchers.IO) {
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
                    .strokeColor(Color.RED)
                    .strokeWidth(3f)
                    .fillColor(Color.argb(50, 255, 0, 0))
            )


            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
        } else {
            hunterMarker?.position = position
            hunterCircle?.center = position
        }
        updateHunterCircleRadius()
    }

    private fun updateHunterCircleRadius() {
        val factor = hunterViewModel.scanRangeMultiplier.value ?: 1.0f
        hunterCircle?.radius = hunterRange.toDouble() * factor
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

    private fun isAbilityActive(abilities: JSONArray?, id: String, durationMs: Long): Boolean {
        if (abilities == null) return false
        val now = System.currentTimeMillis()
        var lastTs: Long = -1
        for (i in 0 until abilities.length()) {
            val a = abilities.optJSONObject(i) ?: continue
            if (a.optString("id") == id) {
                val ts = a.optLong("ts", 0L)
                if (ts > lastTs) lastTs = ts
            }
        }
        return lastTs > 0 && now - lastTs <= durationMs
    }

    private fun updateRunnerMarkers(membersArray: JSONArray) {
        val currentRunnerIds = mutableSetOf<String>()
        val runnerPositions = mutableMapOf<String, LatLng>()

        val revealActive = hunterViewModel.revealActive.value == true
        val trackedId = hunterViewModel.trackedRunnerId.value
        val scanFactor = hunterViewModel.scanRangeMultiplier.value ?: 1.0f

        for (i in 0 until membersArray.length()) {
            val m = membersArray.getJSONObject(i)
            val id = m.optString("userId")

            if (id == userId) continue

            val locObj = m.optJSONObject("loc") ?: continue
            val lat = locObj.optDouble("lat")
            val lon = locObj.optDouble("lon")
            if (lat == 0.0 && lon == 0.0) continue

            val abilitiesArr = m.optJSONArray("abilities")

            val invis = isAbilityActive(abilitiesArr, "invisibility", 15_000L) ||
                    isAbilityActive(abilitiesArr, "stationary", 20_000L)

            val hidden = isAbilityActive(abilitiesArr, "hidden", 10_000L)

            val position = LatLng(lat, lon)
            runnerPositions[id] = position

            val dist = calculateDistance(hunterLat, hunterLon, lat, lon)

            var effectiveRange = hunterRange.toDouble() * scanFactor
            if (hidden) {
                effectiveRange *= 0.5
            }

            val shouldShow =
                (dist <= effectiveRange) || (trackedId == id) || revealActive

            if (!revealActive && invis && trackedId != id) {
                continue
            }

            if (!shouldShow) {
                runnerMarkers[id]?.remove()
                runnerMarkers.remove(id)
                continue
            }

            currentRunnerIds.add(id)

            val name = m.optString("name", "").ifBlank { id }

            if (runnerMarkers.containsKey(id)) {
                runnerMarkers[id]?.position = position
            } else {
                val hue =
                    if (trackedId == id) BitmapDescriptorFactory.HUE_YELLOW
                    else BitmapDescriptorFactory.HUE_BLUE

                val marker = googleMap?.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title("Runner: $name")
                        .icon(BitmapDescriptorFactory.defaultMarker(hue))
                )
                if (marker != null) {
                    runnerMarkers[id] = marker
                }
            }
        }

        hunterViewModel.updateRunnerPositions(runnerPositions)

        val toRemove = runnerMarkers.keys - currentRunnerIds
        toRemove.forEach {
            runnerMarkers[it]?.remove()
            runnerMarkers.remove(it)
        }
    }

    private fun showAbilityDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_ability_selection)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val card1: MaterialCardView = dialog.findViewById(R.id.cardAbility1) // Scan
        val card2: MaterialCardView = dialog.findViewById(R.id.cardAbility2) // Reveal
        val card3: MaterialCardView = dialog.findViewById(R.id.cardAbility3) // Tracker
        val card4: MaterialCardView = dialog.findViewById(R.id.cardAbility5) // Hunter Invisibility
        val btnBack: Button = dialog.findViewById(R.id.btnBack)
        val btnUse: Button = dialog.findViewById(R.id.btnUse)

        selectedAbility = null
        btnUse.isEnabled = false

        fun clearStrokes() {
            card1.strokeColor = Color.TRANSPARENT
            card2.strokeColor = Color.TRANSPARENT
            card3.strokeColor = Color.TRANSPARENT
            card4.strokeColor = Color.TRANSPARENT
        }

        fun select(card: MaterialCardView, ability: PowerupTypes) {
            clearStrokes()
            card.strokeColor = Color.parseColor("#FF1976D2")
            selectedAbility = ability
            btnUse.isEnabled = true
        }

        card1.setOnClickListener { select(card1, PowerupTypes.Scan) }
        card2.setOnClickListener { select(card2, PowerupTypes.Reveal) }
        card3.setOnClickListener { select(card3, PowerupTypes.Tracker) }
        card4.setOnClickListener { select(card4, PowerupTypes.HunterInvisibility) }

        btnBack.setOnClickListener { dialog.dismiss() }

        btnUse.setOnClickListener {
            val ability = selectedAbility
            if (ability != null) {
                useHunterAbility(ability)
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun useHunterAbility(type: PowerupTypes) {
        hunterViewModel.useAbility(type)

        val base = baseUrl ?: return
        val code = roomCode ?: return
        val uid = userId ?: return
        val tok = token ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("userId", uid)
                    put("token", tok)
                    put("abilityId", type.id)
                }.toString()
                val req = Request.Builder()
                    .url("$base/rooms/$code/ability")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().close()
            } catch (_: Exception) { }
        }
    }

    private val PowerupTypes.id: String
        get() = this.id

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
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