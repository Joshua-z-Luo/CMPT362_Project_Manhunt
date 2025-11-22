package joshua_luo.example.cmpt362projectmanhunt

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val client by lazy { OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build() }
    private lateinit var fused: FusedLocationProviderClient
    private var locCallback: LocationCallback? = null
    private var pollJob: Job? = null

    private var userId: String? = null
    private var token: String? = null
    private var currentRoom: String? = null
    private var lastBaseUrl: String? = null

    private lateinit var etBaseUrl: EditText
    private lateinit var etDisplayName: EditText
    private lateinit var etRoomCode: EditText
    private lateinit var btnStartLobby: Button
    private lateinit var btnJoinLobby: Button
    private lateinit var btnLeave: Button
    private lateinit var btnSettings: Button
    private lateinit var btnStartGame: Button
    private lateinit var tvRoom: TextView
    private lateinit var tvUser: TextView
    private lateinit var tvHunterSelection: TextView
    private lateinit var tvHunterRange: TextView
    private lateinit var tvRunnerRange: TextView
    private lateinit var tvAbilityMode: TextView
    private lateinit var tvTimer: TextView
    private lateinit var rv: RecyclerView
    private val adapter = MembersAdapter()

    private val permReq = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fused = LocationServices.getFusedLocationProviderClient(this)
        setContentView(R.layout.activity_main)

        etBaseUrl = findViewById(R.id.etBaseUrl)
        etDisplayName = findViewById(R.id.etDisplayName)
        etRoomCode = findViewById(R.id.etRoomCode)
        btnStartLobby = findViewById(R.id.btnStartLobby)
        btnJoinLobby = findViewById(R.id.btnJoinLobby)
        btnLeave = findViewById(R.id.btnLeave)
        btnSettings = findViewById(R.id.btnSettings)
        btnStartGame = findViewById(R.id.btnStartGame)
        tvRoom = findViewById(R.id.tvRoom)
        tvUser = findViewById(R.id.tvUser)
        tvHunterSelection = findViewById(R.id.tvHunterSelection)
        tvHunterRange = findViewById(R.id.tvHunterRange)
        tvRunnerRange = findViewById(R.id.tvRunnerRange)
        tvAbilityMode = findViewById(R.id.tvAbilityMode)
        tvTimer = findViewById(R.id.tvTimer)
        rv = findViewById(R.id.rvMembers)

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        etRoomCode.filters = arrayOf(InputFilter.AllCaps())
        etRoomCode.doAfterTextChanged {
            val up = it.toString().uppercase()
            if (up != it.toString()) {
                etRoomCode.setText(up)
                etRoomCode.setSelection(up.length)
            }
        }

        btnStartLobby.setOnClickListener {
            permReq.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            startLobbyFlow(etBaseUrl.text.toString().trim(), etDisplayName.text.toString().trim().ifEmpty { null })
        }

        btnJoinLobby.setOnClickListener {
            permReq.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            joinLobbyFlow(
                etBaseUrl.text.toString().trim(),
                etRoomCode.text.toString().trim().uppercase(),
                etDisplayName.text.toString().trim().ifEmpty { null }
            )
        }

        btnLeave.setOnClickListener {
            val base = lastBaseUrl
            val code = currentRoom
            if (!base.isNullOrBlank() && !code.isNullOrBlank()) leaveLobby(base, code) else stopSync()
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnStartGame.setOnClickListener {
            startGame()
        }

        renderStatus()
        updateSettingsDisplay()
    }

    override fun onResume() {
        super.onResume()
        updateSettingsDisplay()
    }

    private fun renderStatus() {
        tvRoom.text = "Current Room: ${currentRoom ?: "-"}"
        tvUser.text = "Your ID: ${userId ?: "-"}"
    }

    private fun updateSettingsDisplay() {
        val prefs = getSharedPreferences("GameSettings", MODE_PRIVATE)

        //Hunter Selection
        tvHunterSelection.text = "Random"

        //Hunter's Range
        val hunterRange = prefs.getString("hunterRange", "50")?.toIntOrNull() ?: 50
        tvHunterRange.text = if (hunterRange >= 1000) {
            "${hunterRange / 1000}km"
        } else {
            "${hunterRange}m"
        }

        //Runner's Range
        val runnerRange = prefs.getString("runnerRange", "100")?.toIntOrNull() ?: 100
        tvRunnerRange.text = if (runnerRange >= 1000) {
            "${runnerRange / 1000}km"
        } else {
            "${runnerRange}m"
        }

        //Ability Mode
        val abilityMode = prefs.getInt("abilityMode", 0)
        tvAbilityMode.text = if (abilityMode == 1) "On" else "Off"

        //Timer
        val timerMinutes = SettingsActivity.getTimerMinutes(this)
        tvTimer.text = "$timerMinutes Min"
    }

    private fun startLobbyFlow(baseUrl: String, name: String?) {
        lastBaseUrl = baseUrl
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val code = createRoom(baseUrl)
                val pair = joinRoomSuspend(baseUrl, code, name)
                withContext(Dispatchers.Main) {
                    userId = pair.first
                    token = pair.second
                    currentRoom = code
                    renderStatus()
                    startSync(baseUrl, code)
                }
            } catch (_: Exception) { }
        }
    }

    private fun joinLobbyFlow(baseUrl: String, code: String, name: String?) {
        lastBaseUrl = baseUrl
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pair = joinRoomSuspend(baseUrl, code, name)
                withContext(Dispatchers.Main) {
                    userId = pair.first
                    token = pair.second
                    currentRoom = code
                    renderStatus()
                    startSync(baseUrl, code)
                }
            } catch (_: Exception) { }
        }
    }

    private fun createRoom(baseUrl: String): String {
        val req = Request.Builder()
            .url("$baseUrl/rooms")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw Exception("Create room failed: ${resp.code} $txt")
            val obj = JSONObject(txt)
            return obj.getString("code")
        }
    }

    private fun joinRoomSuspend(baseUrl: String, code: String, name: String?): Pair<String, String> {
        val body = JSONObject().apply { if (!name.isNullOrBlank()) put("name", name) }.toString()
        val req = Request.Builder()
            .url("$baseUrl/rooms/$code/join")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw Exception("Join failed: ${resp.code} $txt")
            val obj = JSONObject(txt)
            return obj.getString("userId") to obj.getString("token")
        }
    }

    private fun leaveLobby(baseUrl: String, code: String) {
        val uid = userId ?: return stopSync()
        val tok = token ?: return stopSync()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject().apply { put("userId", uid); put("token", tok) }.toString()
                val req = Request.Builder()
                    .url("$baseUrl/rooms/$code/leave")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().close()
            } catch (_: Exception) { }
            withContext(Dispatchers.Main) {
                stopSync()
                currentRoom = null
                userId = null
                token = null
                renderStatus()
            }
        }
    }

    private fun startGame() {
        if (currentRoom == null) {
            Toast.makeText(this, "Please join or create a lobby first!", Toast.LENGTH_SHORT).show()
            return
        }

        val members = adapter.currentList
        if (members.isEmpty()) {
            Toast.makeText(this, "No members in lobby!", Toast.LENGTH_SHORT).show()
            return
        }

        val timerMinutes = SettingsActivity.getTimerMinutes(this)
        val hunterRange = SettingsActivity.getHunterRange(this)
        val runnerRange = SettingsActivity.getRunnerRange(this)
        val abilityMode = SettingsActivity.isAbilityModeEnabled(this)

        val randomHunter = members.random()

        val message = """
            Game Starting!
            Room: $currentRoom
            Timer: $timerMinutes minutes
            Hunter: ${randomHunter.name ?: randomHunter.userId}
            Hunter Range: ${hunterRange}m
            Runner Range: ${runnerRange}m
            Abilities: ${if (abilityMode) "ON" else "OFF"}
        """.trimIndent()

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    }

    @SuppressLint("MissingPermission")
    private fun startSync(baseUrl: String, code: String) {
        val uid = userId ?: return
        val tok = token ?: return

        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(3000L)
            .build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val body = JSONObject().apply {
                            put("userId", uid)
                            put("token", tok)
                            put("lat", loc.latitude)
                            put("lon", loc.longitude)
                        }.toString()
                        val r = Request.Builder()
                            .url("$baseUrl/rooms/$code/loc")
                            .post(body.toRequestBody("application/json".toMediaType()))
                            .build()
                        client.newCall(r).execute().close()
                    } catch (_: Exception) { }
                }
            }
        }
        locCallback?.let { fused.removeLocationUpdates(it) }
        locCallback = cb
        fused.requestLocationUpdates(req, cb, mainLooper)

        pollJob?.cancel()
        pollJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val r = Request.Builder().url("$baseUrl/rooms/$code/state").get().build()
                    client.newCall(r).execute().use { resp ->
                        val txt = resp.body?.string().orEmpty()
                        if (resp.isSuccessful) {
                            val obj = JSONObject(txt)
                            val arr = obj.optJSONArray("members") ?: JSONArray()
                            val list = ArrayList<Member>()
                            for (i in 0 until arr.length()) {
                                val m = arr.getJSONObject(i)
                                val id = m.optString("userId")
                                val name = m.optString("name", "")
                                val updatedAt = m.optLong("updatedAt", 0L)
                                val locObj = m.optJSONObject("loc")
                                val loc = if (locObj != null)
                                    MemberLoc(locObj.optDouble("lat"), locObj.optDouble("lon"), locObj.optLong("ts", 0L))
                                else null
                                list += Member(id, name.ifBlank { null }, loc, updatedAt)
                            }
                            withContext(Dispatchers.Main) { adapter.submitList(list) }
                        }
                    }
                } catch (_: Exception) { }
                delay(3000L)
            }
        }
    }

    private fun stopSync() {
        locCallback?.let { fused.removeLocationUpdates(it) }
        locCallback = null
        pollJob?.cancel()
        pollJob = null
        adapter.submitList(emptyList())
    }
}