package joshua_luo.example.cmpt362projectmanhunt

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputFilter
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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

    companion object {
        private const val SYNC_PREFS_NAME = "manhunt_prefs"
        private const val PREF_BASE_URL = "base_url"
        private const val PREF_ROOM_CODE = "room_code"
        private const val PREF_USER_ID = "user_id"
        private const val PREF_TOKEN = "token"
        private const val PREF_DISPLAY_NAME = "display_name"
        private const val PREF_SYNC_ENABLED = "sync_enabled"
    }

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
    private lateinit var btnMap: Button
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

    private lateinit var syncPrefs: SharedPreferences

    private val permReq = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fused = LocationServices.getFusedLocationProviderClient(this)
        syncPrefs = getSharedPreferences(SYNC_PREFS_NAME, MODE_PRIVATE)
        setContentView(R.layout.activity_main)

        etBaseUrl = findViewById(R.id.etBaseUrl)
        etDisplayName = findViewById(R.id.etDisplayName)
        etRoomCode = findViewById(R.id.etRoomCode)
        btnStartLobby = findViewById(R.id.btnStartLobby)
        btnJoinLobby = findViewById(R.id.btnJoinLobby)
        btnLeave = findViewById(R.id.btnLeave)
        btnSettings = findViewById(R.id.btnSettings)
        btnMap = findViewById(R.id.btnMap)
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

        // Default name is saved username
        val profilePrefs = getSharedPreferences("ProfilePrefs",MODE_PRIVATE)
        val savedName = profilePrefs.getString("username_key", "")
        etDisplayName.setText(savedName)

        btnStartLobby.setOnClickListener {
            permReq.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            val base = etBaseUrl.text.toString().trim()
            if (base.isBlank()) {
                Toast.makeText(this, "Please enter Base URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val name = etDisplayName.text.toString().trim().ifEmpty { null }
            startLobbyFlow(base, name)
        }

        btnJoinLobby.setOnClickListener {
            permReq.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            val base = etBaseUrl.text.toString().trim()
            val code = etRoomCode.text.toString().trim().uppercase()
            if (base.isBlank()) {
                Toast.makeText(this, "Please enter Base URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (code.length != 6) {
                Toast.makeText(this, "Room code must be 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val name = etDisplayName.text.toString().trim().ifEmpty { null }
            joinLobbyFlow(base, code, name)
        }

        btnLeave.setOnClickListener {
            val base = lastBaseUrl
            val code = currentRoom
            if (!base.isNullOrBlank() && !code.isNullOrBlank()) {
                leaveLobby(base, code)
            } else {
                stopSync()
                stopSyncServiceAndClearPrefs()
            }
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnMap.setOnClickListener {
            // TODO: map boundary setting functionality
            Toast.makeText(this, "Map boundary setting", Toast.LENGTH_SHORT).show()
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

        // Hunter Selection
        tvHunterSelection.text = "Random"

        // Hunter's Range
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

        // Ability Mode
        val abilityMode = prefs.getInt("abilityMode", 0)
        tvAbilityMode.text = if (abilityMode == 1) "On" else "Off"

        // Timer
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
                    updateSyncPrefsAndStartService(
                        baseUrl = baseUrl,
                        roomCode = code,
                        userId = userId!!,
                        token = token!!,
                        displayName = name
                    )
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
                    updateSyncPrefsAndStartService(
                        baseUrl = baseUrl,
                        roomCode = code,
                        userId = userId!!,
                        token = token!!,
                        displayName = name
                    )
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
        val uid = userId ?: return run {
            stopSync()
            stopSyncServiceAndClearPrefs()
        }
        val tok = token ?: return run {
            stopSync()
            stopSyncServiceAndClearPrefs()
        }
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
                stopSyncServiceAndClearPrefs()
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

        //randomly select hunter
        val randomHunter = members.random()

        val prefs = getSharedPreferences("GameData", MODE_PRIVATE)
        prefs.edit().apply {
            putString("token", token)
            apply()
        }

        val intent = Intent(this, CountdownActivity::class.java).apply {
            putExtra("hunterId", randomHunter.userId)
            putExtra("userId", userId)
            putExtra("roomCode", currentRoom)
            putExtra("baseUrl", lastBaseUrl)
            putExtra("timerMinutes", timerMinutes)
            putExtra("hunterRange", hunterRange)
            putExtra("runnerRange", runnerRange)
            putExtra("abilityMode", abilityMode)
        }
        startActivity(intent)
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
                                    MemberLoc(
                                        locObj.optDouble("lat"),
                                        locObj.optDouble("lon"),
                                        locObj.optLong("ts", 0L)
                                    )
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

    private fun updateSyncPrefsAndStartService(
        baseUrl: String,
        roomCode: String,
        userId: String,
        token: String,
        displayName: String?
    ) {
        syncPrefs.edit()
            .putString(PREF_BASE_URL, baseUrl)
            .putString(PREF_ROOM_CODE, roomCode)
            .putString(PREF_USER_ID, userId)
            .putString(PREF_TOKEN, token)
            .putString(PREF_DISPLAY_NAME, displayName ?: "")
            .putBoolean(PREF_SYNC_ENABLED, true)
            .apply()

        val intent = Intent(this, SyncService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopSyncServiceAndClearPrefs() {
        syncPrefs.edit()
            .remove(PREF_ROOM_CODE)
            .remove(PREF_USER_ID)
            .remove(PREF_TOKEN)
            .putBoolean(PREF_SYNC_ENABLED, false)
            .apply()

        val intent = Intent(this, SyncService::class.java)
        stopService(intent)
    }
}
