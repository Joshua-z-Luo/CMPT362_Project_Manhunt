package joshua_luo.example.cmpt362projectmanhunt

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Window
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
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

class RunnerGameActivity : FragmentActivity(), OnMapReadyCallback {

    private val client by lazy { OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build() }
    private lateinit var fused: FusedLocationProviderClient
    private var locCallback: LocationCallback? = null
    private var pollJob: Job? = null
    private var gameTimer: CountDownTimer? = null
    private var googleMap: GoogleMap? = null
    private var runnerMarker: Marker? = null
    private var runnerCircle: Circle? = null
    private var hunterMarker: Marker? = null

    private lateinit var tvGameTimer: TextView
    private lateinit var abilityButton: Button
    private lateinit var btnCaught: Button

    private var selectedAbility: Int = -1
    private var userId: String? = null
    private var token: String? = null
    private var roomCode: String? = null
    private var baseUrl: String? = null
    private var timerMinutes: Int = 30
    private var runnerRange: Int = 100
    private var abilityMode: Boolean = false
    private var hunterId: String? = null

    private var runnerLat: Double = 0.0
    private var runnerLon: Double = 0.0

    private val runnerViewModel: RunnerViewModel by viewModels()

    private val permReq = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_runnergame)

        fused = LocationServices.getFusedLocationProviderClient(this)
        tvGameTimer = findViewById(R.id.tvGameTimer)
        abilityButton = findViewById(R.id.abilityButton)
        btnCaught = findViewById(R.id.btnCaught)

        userId = intent.getStringExtra("userId")
        roomCode = intent.getStringExtra("roomCode")
        baseUrl = intent.getStringExtra("baseUrl")
        timerMinutes = intent.getIntExtra("timerMinutes", 30)
        runnerRange = intent.getIntExtra("runnerRange", 100)
        abilityMode = intent.getBooleanExtra("abilityMode", false)
        hunterId = intent.getStringExtra("hunterId")

        val prefs = getSharedPreferences("GameData", MODE_PRIVATE)
        token = prefs.getString("token", null)

        permReq.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        btnCaught.setOnClickListener {
            sendStatusUpdate(health = 0, role = "spectator")
            val intent = Intent(this, CaughtActivity::class.java)
            startActivity(intent)
            finish()
        }

        abilityButton.setOnClickListener {
            if (abilityMode) {
                showAbilityDialog()
            } else {
                Toast.makeText(this, "Abilities disabled for this game", Toast.LENGTH_SHORT).show()
            }
        }

        observeRunnerAbilities()
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
        startPollingHunter()
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
                Toast.makeText(
                    this@RunnerGameActivity,
                    "Time's up! Runners win!",
                    Toast.LENGTH_LONG
                ).show()

                val intent = Intent(this@RunnerGameActivity, GameEndActivity::class.java)
                intent.putExtra("finalTime", "0min 0sec")
                intent.putExtra("isHunter", false)
                startActivity(intent)
                finish()
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        val uid = userId ?: return
        val base = baseUrl ?: return
        val code = roomCode ?: return

        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                runnerLat = loc.latitude
                runnerLon = loc.longitude

                val position = LatLng(runnerLat, runnerLon)
                runnerViewModel.updateRunnerPosition(position)
                updateRunnerMarker(position)

                if (runnerViewModel.invisibleActive.value == true) return

                val tok = token ?: return

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val body = JSONObject().apply {
                            put("userId", uid)
                            put("token", tok)
                            put("lat", runnerLat)
                            put("lon", runnerLon)
                        }.toString()
                        val r = Request.Builder()
                            .url("$base/rooms/$code/loc")
                            .post(body.toRequestBody("application/json".toMediaType()))
                            .build()
                        client.newCall(r).execute().close()
                    } catch (_: Exception) {
                    }
                }
            }
        }

        locCallback = cb
        fused.requestLocationUpdates(req, cb, mainLooper)
    }

    private fun updateRunnerMarker(position: LatLng) {
        if (runnerMarker == null) {
            runnerMarker = googleMap?.addMarker(
                MarkerOptions()
                    .position(position)
                    .title("You (Runner)")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            )

            runnerCircle = googleMap?.addCircle(
                CircleOptions()
                    .center(position)
                    .radius(runnerRange.toDouble())
                    .strokeColor(Color.BLUE)
                    .strokeWidth(3f)
                    .fillColor(Color.argb(50, 0, 0, 255))
            )

            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
        } else {
            runnerMarker?.position = position
            runnerCircle?.center = position
        }
    }

    private fun startPollingHunter() {
        val base = baseUrl ?: return
        val code = roomCode ?: return
        val hId = hunterId ?: return

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
                                updateHunterMarker(arr, hId)
                            }
                        }
                    }
                } catch (_: Exception) {
                }
                delay(3000L)
            }
        }
    }

    private fun updateHunterMarker(membersArray: JSONArray, hunterId: String) {
        for (i in 0 until membersArray.length()) {
            val m = membersArray.getJSONObject(i)
            val id = m.optString("userId")

            if (id != hunterId) continue

            val locObj = m.optJSONObject("loc") ?: continue
            val lat = locObj.optDouble("lat")
            val lon = locObj.optDouble("lon")

            if (lat == 0.0 && lon == 0.0) continue

            val distance = calculateDistance(runnerLat, runnerLon, lat, lon)

            if (distance <= runnerRange) {
                val position = LatLng(lat, lon)
                val name = m.optString("name", "").ifBlank { "Hunter" }

                if (hunterMarker == null) {
                    hunterMarker = googleMap?.addMarker(
                        MarkerOptions()
                            .position(position)
                            .title("Hunter: $name")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    )
                } else {
                    hunterMarker?.position = position
                }
            } else {
                hunterMarker?.remove()
                hunterMarker = null
            }
            break
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun observeRunnerAbilities() {
        runnerViewModel.invisibleActive.observe(this) { active ->
            runnerMarker?.isVisible = !active
            runnerCircle?.isVisible = !active
            if (active) {
                Toast.makeText(this, "Runner invisibility activated", Toast.LENGTH_SHORT).show()
            }
        }

        runnerViewModel.isCurrentlyHiddenByStationary.observe(this) { hidden ->
            if (runnerViewModel.invisibleActive.value == true) return@observe
            runnerMarker?.isVisible = !hidden
        }

        runnerViewModel.flashBangActive.observe(this) { active ->
            if (active) {
                Toast.makeText(
                    this,
                    "Flash Bang used (hunter radar disturbed in full version)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showAbilityDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_runner_ability_selection)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val cardAbility1: MaterialCardView = dialog.findViewById(R.id.cardAbility1)
        val cardAbility2: MaterialCardView = dialog.findViewById(R.id.cardAbility2)
        val cardAbility3: MaterialCardView = dialog.findViewById(R.id.cardAbility3)
        val btnBack: Button = dialog.findViewById(R.id.btnBack)
        val btnUse: Button = dialog.findViewById(R.id.btnUse)

        selectedAbility = -1
        btnUse.isEnabled = false

        fun clearStrokes() {
            cardAbility1.strokeColor = Color.TRANSPARENT
            cardAbility2.strokeColor = Color.TRANSPARENT
            cardAbility3.strokeColor = Color.TRANSPARENT
        }

        fun updateCardSelection(selected: MaterialCardView, ability: Int) {
            clearStrokes()
            selected.strokeColor = Color.parseColor("#FF1976D2")
            selectedAbility = ability
            btnUse.isEnabled = true
        }

        // 0 -> Invisibility, 1 -> Stationary hide, 2 -> Shield
        cardAbility1.setOnClickListener { updateCardSelection(cardAbility1, 0) }
        cardAbility2.setOnClickListener { updateCardSelection(cardAbility2, 1) }
        cardAbility3.setOnClickListener { updateCardSelection(cardAbility3, 2) }

        btnBack.setOnClickListener { dialog.dismiss() }

        btnUse.setOnClickListener {
            when (selectedAbility) {
                0 -> {
                    runnerViewModel.useAbility(PowerupTypes.Invisibility)
                    sendAbilityActivation("invisibility")
                }
                1 -> {
                    runnerViewModel.useAbility(PowerupTypes.Stationary)
                    sendAbilityActivation("stationary")
                    Toast.makeText(this, "Stationary Hide activated", Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    runnerViewModel.useAbility(PowerupTypes.Shield)
                    sendAbilityActivation("shield")
                    Toast.makeText(this, "Shield acquired", Toast.LENGTH_SHORT).show()
                }
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun sendAbilityActivation(abilityId: String) {
        val base = baseUrl ?: return
        val code = roomCode ?: return
        val uid = userId ?: return
        val tok = token ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("userId", uid)
                    put("token", tok)
                    put("abilityId", abilityId)
                }.toString()
                val req = Request.Builder()
                    .url("$base/rooms/$code/ability")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().close()
            } catch (_: Exception) {
            }
        }
    }

    private fun sendStatusUpdate(health: Int? = null, role: String? = null, team: String? = null) {
        val base = baseUrl ?: return
        val code = roomCode ?: return
        val uid = userId ?: return
        val tok = token ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("userId", uid)
                    put("token", tok)
                    health?.let { put("health", it) }
                    role?.let { put("role", it) }
                    team?.let { put("team", it) }
                }.toString()
                val req = Request.Builder()
                    .url("$base/rooms/$code/status")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().close()
            } catch (_: Exception) {
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locCallback?.let { fused.removeLocationUpdates(it) }
        pollJob?.cancel()
        gameTimer?.cancel()
    }
}
