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

    private var selectedAbilityIndex: Int = -1

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

    private var gameEnded = false
    private var totalDistanceMeters: Double = 0.0
    private var lastLatLng: LatLng? = null

    private lateinit var runnerViewModel: RunnerViewModel

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

        runnerViewModel = ViewModelProvider(this)[RunnerViewModel::class.java]

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

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Set up caught button click listener
        btnCaught.setOnClickListener {
            if (runnerViewModel.consumeShieldIfActive()) {
                Toast.makeText(this, "Shield absorbed the tag!", Toast.LENGTH_SHORT).show()
            } else {
                //health to 0 immediately
                val uid = userId ?: return@setOnClickListener
                val tok = token ?: return@setOnClickListener
                val code = roomCode ?: return@setOnClickListener
                val base = baseUrl ?: return@setOnClickListener

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val body = JSONObject().apply {
                            put("userId", uid)
                            put("token", tok)
                            put("health", 0)
                        }.toString()

                        val request = Request.Builder()
                            .url("$base/rooms/$code/status")
                            .post(body.toRequestBody("application/json".toMediaType()))
                            .build()

                        client.newCall(request).execute().close()

                        withContext(Dispatchers.Main) {
                            val intent = Intent(this@RunnerGameActivity, GameEndActivity::class.java)
                            intent.putExtra("finalTime", tvGameTimer.text.toString())
                            intent.putExtra("isHunter", false)
                            intent.putExtra("distanceMeters", totalDistanceMeters)
                            intent.putExtra("isDead", true)
                            startActivity(intent)
                            finish()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // Set up ability button click listener
        abilityButton.setOnClickListener {
            if (!abilityMode) {
                Toast.makeText(this, "Abilities are disabled for this match.", Toast.LENGTH_SHORT).show()
            } else {
                showAbilityDialog()
            }
        }

        startGameTimer()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        try {
            googleMap?.isMyLocationEnabled = true
        } catch (_: SecurityException) { }
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
                if (!gameEnded) {
                    gameEnded = true
                    Toast.makeText(this@RunnerGameActivity, "Time's up! Runners win!", Toast.LENGTH_LONG).show()
                    navigateToGameEnd("0min 0sec")
                }
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
                runnerLat = loc.latitude
                runnerLon = loc.longitude

                val pos = LatLng(runnerLat, runnerLon)

                //calculate distance travelled
                lastLatLng?.let { lastPos ->
                    val distance = calculateDistance(lastPos.latitude , lastPos.longitude, runnerLat, runnerLon)
                    totalDistanceMeters += distance
                }
                lastLatLng = pos

                runnerViewModel.updateRunnerPosition(pos)
                updateRunnerMarker(runnerLat, runnerLon)

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
                    } catch (_: Exception) { }
                }
            }
        }

        locCallback = cb
        fused.requestLocationUpdates(req, cb, mainLooper)
    }

    private fun updateRunnerMarker(lat: Double, lon: Double) {
        val position = LatLng(lat, lon)

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
            while (isActive && !gameEnded) {
                try {
                    val r = Request.Builder().url("$base/rooms/$code/state").get().build()
                    client.newCall(r).execute().use { resp ->
                        val txt = resp.body?.string().orEmpty()
                        if (resp.isSuccessful) {
                            val obj = JSONObject(txt)
                            val arr = obj.optJSONArray("members") ?: JSONArray()
                            withContext(Dispatchers.Main) {
                                updateHunterMarker(arr, hId)
                                checkGameEnd(arr)
                            }
                        }
                    }
                } catch (_: Exception) { }
                delay(3000L)
            }
        }
    }

    private fun checkGameEnd(membersArray: JSONArray) {
        if (gameEnded) return

        var totalRunners = 0
        var deadRunners = 0

        for (i in 0 until membersArray.length()) {
            val member = membersArray.getJSONObject(i)
            val memberId = member.optString("userId")

            if (memberId == hunterId) continue

            totalRunners++
            val status = member.optJSONObject("status")
            val health = status?.optInt("health", 100 ) ?: 100

            if (health <= 0) {
                deadRunners++
            }
        }

        //if all runners are dead, hunters win
        if (totalRunners > 0 && deadRunners >= totalRunners) {
            gameEnded = true
            val remainingTime = tvGameTimer.text.toString()
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(
                    this@RunnerGameActivity,
                    "All runners caught! Hunters win!",
                    Toast.LENGTH_LONG
                ).show()
                navigateToGameEnd(remainingTime)
            }
        }
    }

    private fun isHunterInvisActive(abilities: JSONArray?): Boolean {
        if (abilities == null) return false
        val now = System.currentTimeMillis()
        var lastTs: Long = -1
        for (i in 0 until abilities.length()) {
            val a = abilities.optJSONObject(i) ?: continue
            if (a.optString("id") == "hunterInvisibility") {
                val ts = a.optLong("ts", 0L)
                if (ts > lastTs) lastTs = ts
            }
        }
        return lastTs > 0 && now - lastTs <= 15_000L
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

            val abilitiesArr = m.optJSONArray("abilities")
            val hunterInvis = isHunterInvisActive(abilitiesArr)

            if (hunterInvis) {
                hunterMarker?.remove()
                hunterMarker = null
                break
            }

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

    private fun showAbilityDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_runner_ability_selection)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val card1: MaterialCardView = dialog.findViewById(R.id.cardAbility1) // Invisibility
        val card2: MaterialCardView = dialog.findViewById(R.id.cardAbility2) // Hidden
        val card3: MaterialCardView = dialog.findViewById(R.id.cardAbility3) // Stationary
        val card4: MaterialCardView = dialog.findViewById(R.id.cardAbility4) // Shield
        val btnBack: Button = dialog.findViewById(R.id.btnBack)
        val btnUse: Button = dialog.findViewById(R.id.btnUse)

        selectedAbilityIndex = -1
        btnUse.isEnabled = false

        fun clearStrokes() {
            card1.strokeColor = Color.TRANSPARENT
            card2.strokeColor = Color.TRANSPARENT
            card3.strokeColor = Color.TRANSPARENT
            card4.strokeColor = Color.TRANSPARENT
        }

        fun select(card: MaterialCardView, idx: Int) {
            clearStrokes()
            card.strokeColor = Color.parseColor("#FF1976D2")
            selectedAbilityIndex = idx
            btnUse.isEnabled = true
        }

        card1.setOnClickListener { select(card1, 0) }
        card2.setOnClickListener { select(card2, 1) }
        card3.setOnClickListener { select(card3, 2) }
        card4.setOnClickListener { select(card4, 3) }

        btnBack.setOnClickListener { dialog.dismiss() }

        btnUse.setOnClickListener {
            when (selectedAbilityIndex) {
                0 -> useRunnerAbility(PowerupTypes.Invisibility)
                1 -> useRunnerAbility(PowerupTypes.Hidden)
                2 -> useRunnerAbility(PowerupTypes.Stationary)
                3 -> useRunnerAbility(PowerupTypes.Shield)
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun useRunnerAbility(type: PowerupTypes) {
        runnerViewModel.useAbility(type)

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
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun navigateToGameEnd(finalTime: String) {
        val intent = Intent(this, GameEndActivity::class.java)
        intent.putExtra("finalTime",  finalTime)
        intent.putExtra("isHunter", false)
        intent.putExtra("distanceMeters", totalDistanceMeters)
        intent.putExtra("isDead", false )
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        locCallback?.let { fused.removeLocationUpdates(it) }
        pollJob?.cancel()
        gameTimer?.cancel()
    }
}
