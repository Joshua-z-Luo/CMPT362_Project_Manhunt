package joshua_luo.example.cmpt362projectmanhunt

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputFilter
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val PREFS_NAME = "manhunt_prefs"
        private const val PREF_BASE_URL = "base_url"
        private const val PREF_ROOM_CODE = "room_code"
        private const val PREF_USER_ID = "user_id"
        private const val PREF_TOKEN = "token"
        private const val PREF_DISPLAY_NAME = "display_name"
        private const val PREF_TEAM = "team"
        private const val PREF_ROLE = "role"
        private const val PREF_HEALTH = "health"
        private const val PREF_ROOM_STATE_JSON = "room_state_json"
        private const val PREF_PLAYER_LOCATIONS = "player_locations_json"
        private const val PREF_PLAYER_ABILITIES = "player_abilities_json"
        private const val PREF_ROOM_SETTINGS_JSON = "room_settings_json"
        private const val PREF_PENDING_ABILITY_ID = "pending_ability_id"
        private const val PREF_SYNC_ENABLED = "sync_enabled"
    }

    private val client by lazy { OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build() }
    private lateinit var prefs: SharedPreferences

    private lateinit var etBaseUrl: EditText
    private lateinit var etDisplayName: EditText
    private lateinit var etRoomCode: EditText
    private lateinit var btnStartLobby: Button
    private lateinit var btnJoinLobby: Button
    private lateinit var btnLeave: Button
    private lateinit var btnTestAbility: Button
    private lateinit var btnTestSettings: Button
    private lateinit var tvRoom: TextView
    private lateinit var tvUser: TextView
    private lateinit var tvSettings: TextView
    private lateinit var rv: RecyclerView
    private val adapter = MembersAdapter()

    private val permReq = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        setContentView(R.layout.activity_main)

        etBaseUrl = findViewById(R.id.etBaseUrl)
        etDisplayName = findViewById(R.id.etDisplayName)
        etRoomCode = findViewById(R.id.etRoomCode)
        btnStartLobby = findViewById(R.id.btnStartLobby)
        btnJoinLobby = findViewById(R.id.btnJoinLobby)
        btnLeave = findViewById(R.id.btnLeave)
        btnTestAbility = findViewById(R.id.btnTestAbility)
        btnTestSettings = findViewById(R.id.btnTestSettings)
        tvRoom = findViewById(R.id.tvRoom)
        tvUser = findViewById(R.id.tvUser)
        tvSettings = findViewById(R.id.tvSettings)
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

        restoreUiFromPrefs()
        renderStatus()
        renderMembersFromPrefs()
        renderSettingsFromPrefs()

        btnStartLobby.setOnClickListener {
            permReq.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            val baseUrl = etBaseUrl.text.toString().trim()
            val name = etDisplayName.text.toString().trim().ifEmpty { null }
            if (baseUrl.isNotBlank()) {
                startLobbyFlow(baseUrl, name)
            }
        }

        btnJoinLobby.setOnClickListener {
            permReq.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            val baseUrl = etBaseUrl.text.toString().trim()
            val code = etRoomCode.text.toString().trim().uppercase()
            val name = etDisplayName.text.toString().trim().ifEmpty { null }
            if (baseUrl.isNotBlank() && code.isNotBlank()) {
                joinLobbyFlow(baseUrl, code, name)
            }
        }

        btnLeave.setOnClickListener {
            val base = prefs.getString(PREF_BASE_URL, null)
            val code = prefs.getString(PREF_ROOM_CODE, null)
            val uid = prefs.getString(PREF_USER_ID, null)
            val tok = prefs.getString(PREF_TOKEN, null)
            if (!base.isNullOrBlank() && !code.isNullOrBlank() && !uid.isNullOrBlank() && !tok.isNullOrBlank()) {
                leaveLobby(base, code, uid, tok)
            } else {
                clearSyncPrefs()
                stopSyncService()
            }
        }

        btnTestAbility.setOnClickListener {
            triggerAbility("scan_test")
        }

        btnTestSettings.setOnClickListener {
            toggleGameStartSetting()
        }

        val roomCode = prefs.getString(PREF_ROOM_CODE, null)
        val syncEnabled = prefs.getBoolean(PREF_SYNC_ENABLED, false)
        if (!roomCode.isNullOrBlank() && syncEnabled) {
            startSyncService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun restoreUiFromPrefs() {
        etBaseUrl.setText(prefs.getString(PREF_BASE_URL, ""))
        etDisplayName.setText(prefs.getString(PREF_DISPLAY_NAME, ""))
        etRoomCode.setText(prefs.getString(PREF_ROOM_CODE, ""))
    }

    private fun renderStatus() {
        val room = prefs.getString(PREF_ROOM_CODE, null)
        val user = prefs.getString(PREF_USER_ID, null)
        tvRoom.text = "Current Room: ${room ?: "-"}"
        tvUser.text = "Your ID: ${user ?: "-"}"
    }

    private fun renderMembersFromPrefs() {
        val json = prefs.getString(PREF_ROOM_STATE_JSON, null) ?: return
        try {
            val obj = JSONObject(json)
            val arr = obj.optJSONArray("members") ?: JSONArray()
            val list = ArrayList<Member>()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val userId = m.optString("userId")
                val name = m.optString("name", "")
                val updatedAt = m.optLong("updatedAt", 0L)

                val locObj = m.optJSONObject("loc")
                val loc = if (locObj != null) {
                    MemberLoc(
                        lat = locObj.optDouble("lat"),
                        lon = locObj.optDouble("lon"),
                        ts = locObj.optLong("ts", 0L)
                    )
                } else {
                    null
                }

                val abilitiesArr = m.optJSONArray("abilities") ?: JSONArray()
                val abilities = ArrayList<MemberAbility>()
                for (j in 0 until abilitiesArr.length()) {
                    val a = abilitiesArr.getJSONObject(j)
                    abilities += MemberAbility(
                        id = a.optString("id"),
                        ts = a.optLong("ts", 0L)
                    )
                }

                val statusObj = m.optJSONObject("status")
                val status = if (statusObj != null) {
                    MemberStatus(
                        team = statusObj.optString("team", null),
                        role = statusObj.optString("role", null),
                        health = if (statusObj.has("health")) statusObj.optInt("health") else null
                    )
                } else {
                    null
                }

                list += Member(
                    userId = userId,
                    name = name.ifBlank { null },
                    loc = loc,
                    updatedAt = updatedAt,
                    abilities = abilities,
                    status = status
                )
            }
            adapter.submitList(list)
        } catch (_: Exception) {
        }
    }

    private fun renderSettingsFromPrefs() {
        val raw = prefs.getString(PREF_ROOM_SETTINGS_JSON, null)
        if (raw.isNullOrBlank()) {
            tvSettings.text = "Settings: -"
            return
        }
        try {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("settings") ?: JSONArray()
            if (arr.length() == 0) {
                tvSettings.text = "Settings: -"
                return
            }
            val lines = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val key = s.optString("key")
                val value = s.optString("value")
                lines += "$key = $value"
            }
            tvSettings.text = lines.joinToString("\n")
        } catch (_: Exception) {
            tvSettings.text = "Settings: (invalid JSON)"
        }
    }

    private fun startLobbyFlow(baseUrl: String, name: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val code = createRoom(baseUrl)
                val pair = joinRoomSuspend(baseUrl, code, name)
                withContext(Dispatchers.Main) {
                    prefs.edit()
                        .putString(PREF_BASE_URL, baseUrl)
                        .putString(PREF_ROOM_CODE, code)
                        .putString(PREF_USER_ID, pair.first)
                        .putString(PREF_TOKEN, pair.second)
                        .putString(PREF_DISPLAY_NAME, name ?: "")
                        .putBoolean(PREF_SYNC_ENABLED, true)
                        .apply()
                    renderStatus()
                    startSyncService()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun joinLobbyFlow(baseUrl: String, code: String, name: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pair = joinRoomSuspend(baseUrl, code, name)
                withContext(Dispatchers.Main) {
                    prefs.edit()
                        .putString(PREF_BASE_URL, baseUrl)
                        .putString(PREF_ROOM_CODE, code)
                        .putString(PREF_USER_ID, pair.first)
                        .putString(PREF_TOKEN, pair.second)
                        .putString(PREF_DISPLAY_NAME, name ?: "")
                        .putBoolean(PREF_SYNC_ENABLED, true)
                        .apply()
                    renderStatus()
                    startSyncService()
                }
            } catch (_: Exception) {
            }
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
        val bodyObj = JSONObject()
        if (!name.isNullOrBlank()) bodyObj.put("name", name)
        val body = bodyObj.toString()
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

    private fun leaveLobby(baseUrl: String, code: String, uid: String, tok: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("userId", uid)
                    put("token", tok)
                }.toString()
                val req = Request.Builder()
                    .url("$baseUrl/rooms/$code/leave")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().close()
            } catch (_: Exception) {
            }
            withContext(Dispatchers.Main) {
                clearSyncPrefs()
                stopSyncService()
                renderStatus()
                adapter.submitList(emptyList())
            }
        }
    }

    private fun clearSyncPrefs() {
        prefs.edit()
            .remove(PREF_ROOM_CODE)
            .remove(PREF_USER_ID)
            .remove(PREF_TOKEN)
            .putBoolean(PREF_SYNC_ENABLED, false)
            .remove(PREF_ROOM_STATE_JSON)
            .remove(PREF_PLAYER_LOCATIONS)
            .remove(PREF_PLAYER_ABILITIES)
            .remove(PREF_ROOM_SETTINGS_JSON)
            .remove(PREF_PENDING_ABILITY_ID)
            .apply()
    }

    private fun startSyncService() {
        val intent = Intent(this, SyncService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopSyncService() {
        val intent = Intent(this, SyncService::class.java)
        stopService(intent)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == null) return
        if (key == PREF_ROOM_STATE_JSON) {
            renderMembersFromPrefs()
        }
        if (key == PREF_ROOM_CODE || key == PREF_USER_ID) {
            renderStatus()
        }
        if (key == PREF_ROOM_SETTINGS_JSON) {
            renderSettingsFromPrefs()
        }
    }

    fun updateTeamRoleHealth(team: String?, role: String?, health: Int?) {
        prefs.edit()
            .putString(PREF_TEAM, team ?: "")
            .putString(PREF_ROLE, role ?: "")
            .apply()
        if (health != null) {
            prefs.edit().putInt(PREF_HEALTH, health).apply()
        }
    }

    fun triggerAbility(abilityId: String) {
        prefs.edit()
            .putString(PREF_PENDING_ABILITY_ID, abilityId)
            .apply()
    }

    fun updateRoomSettings(settings: JSONObject) {
        prefs.edit()
            .putString(PREF_ROOM_SETTINGS_JSON, settings.toString())
            .apply()
    }

    private fun toggleGameStartSetting() {
        val current = prefs.getString(PREF_ROOM_SETTINGS_JSON, null)
        val settingsArr = if (current.isNullOrBlank()) {
            JSONArray()
        } else {
            try {
                val root = JSONObject(current)
                root.optJSONArray("settings") ?: JSONArray()
            } catch (e: Exception) {
                JSONArray()
            }
        }

        var gameStartObj: JSONObject? = null
        for (i in 0 until settingsArr.length()) {
            val s = settingsArr.getJSONObject(i)
            if (s.optString("key") == "gameStart") {
                gameStartObj = s
                break
            }
        }

        if (gameStartObj == null) {
            gameStartObj = JSONObject()
                .put("key", "gameStart")
                .put("value", "on")
            settingsArr.put(gameStartObj)
        } else {
            val currentValue = gameStartObj.optString("value", "off")
            val next = if (currentValue == "on") "off" else "on"
            gameStartObj.put("value", next)
        }

        var abilitiesObj: JSONObject? = null
        for (i in 0 until settingsArr.length()) {
            val s = settingsArr.getJSONObject(i)
            if (s.optString("key") == "abilities") {
                abilitiesObj = s
                break
            }
        }
        if (abilitiesObj == null) {
            settingsArr.put(
                JSONObject()
                    .put("key", "abilities")
                    .put("value", "on")
            )
        }

        var fogObj: JSONObject? = null
        for (i in 0 until settingsArr.length()) {
            val s = settingsArr.getJSONObject(i)
            if (s.optString("key") == "fog") {
                fogObj = s
                break
            }
        }
        if (fogObj == null) {
            settingsArr.put(
                JSONObject()
                    .put("key", "fog")
                    .put("value", "off")
            )
        }

        val root = JSONObject().put("settings", settingsArr)
        updateRoomSettings(root)
        renderSettingsFromPrefs()
    }
}
