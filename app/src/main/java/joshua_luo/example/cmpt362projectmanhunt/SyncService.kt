package joshua_luo.example.cmpt362projectmanhunt

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SyncService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val PREFS_NAME = "manhunt_prefs"
        private const val PREF_BASE_URL = "base_url"
        private const val PREF_ROOM_CODE = "room_code"
        private const val PREF_USER_ID = "user_id"
        private const val PREF_TOKEN = "token"
        private const val PREF_TEAM = "team"
        private const val PREF_ROLE = "role"
        private const val PREF_HEALTH = "health"
        private const val PREF_ROOM_STATE_JSON = "room_state_json"
        private const val PREF_PLAYER_LOCATIONS = "player_locations_json"
        private const val PREF_PLAYER_ABILITIES = "player_abilities_json"
        private const val PREF_ROOM_SETTINGS_JSON = "room_settings_json"
        private const val PREF_PENDING_ABILITY_ID = "pending_ability_id"
        private const val PREF_SYNC_ENABLED = "sync_enabled"

        private const val CHANNEL_ID = "manhunt_sync_channel"
        private const val NOTIFICATION_ID = 1001

        private const val LOCATION_UPDATE_INTERVAL_MS = 2000L
        private const val LOCATION_MIN_UPDATE_INTERVAL_MS = 1000L
        private const val POLL_INTERVAL_MS = 1000L
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var fused: FusedLocationProviderClient
    private var locCallback: LocationCallback? = null
    private val client by lazy { OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build() }

    private val serviceJob: Job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var pollJob: Job? = null

    private var lastTeamSent: String? = null
    private var lastRoleSent: String? = null
    private var lastHealthSent: Int? = null

    @Volatile
    private var updatingSettingsFromServer: Boolean = false

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        fused = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startOrStopSyncFromPrefs()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startOrStopSyncFromPrefs()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        stopLocation()
        stopPolling()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Manhunt sync running")
            .setContentText("Location and room state are being synced")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Manhunt Sync",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun startOrStopSyncFromPrefs() {
        val syncEnabled = prefs.getBoolean(PREF_SYNC_ENABLED, false)
        val base = prefs.getString(PREF_BASE_URL, null)
        val code = prefs.getString(PREF_ROOM_CODE, null)
        val uid = prefs.getString(PREF_USER_ID, null)
        val tok = prefs.getString(PREF_TOKEN, null)
        if (syncEnabled && !base.isNullOrBlank() && !code.isNullOrBlank() && !uid.isNullOrBlank() && !tok.isNullOrBlank()) {
            startLocation(base, code, uid, tok)
            startPolling(base, code, uid, tok)
        } else {
            stopLocation()
            stopPolling()
        }
    }

    private fun startLocation(baseUrl: String, code: String, userId: String, token: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LOCATION_MIN_UPDATE_INTERVAL_MS)
            .build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                scope.launch {
                    try {
                        val body = JSONObject().apply {
                            put("userId", userId)
                            put("token", token)
                            put("lat", loc.latitude)
                            put("lon", loc.longitude)
                        }.toString()
                        val r = Request.Builder()
                            .url("$baseUrl/rooms/$code/loc")
                            .post(body.toRequestBody("application/json".toMediaType()))
                            .build()
                        client.newCall(r).execute().close()
                    } catch (_: Exception) {
                    }
                }
            }
        }
        locCallback?.let { fused.removeLocationUpdates(it) }
        locCallback = cb
        fused.requestLocationUpdates(req, cb, Looper.getMainLooper())
    }

    private fun stopLocation() {
        locCallback?.let { fused.removeLocationUpdates(it) }
        locCallback = null
    }

    private fun startPolling(baseUrl: String, code: String, userId: String, token: String) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                sendPendingAbilityIfAny(baseUrl, code, userId, token)
                sendStatusIfChanged(baseUrl, code, userId, token)
                try {
                    val r = Request.Builder()
                        .url("$baseUrl/rooms/$code/state")
                        .get()
                        .build()
                    client.newCall(r).execute().use { resp ->
                        val txt = resp.body?.string().orEmpty()
                        if (resp.isSuccessful && txt.isNotBlank()) {
                            val obj = JSONObject(txt)
                            val membersArr = obj.optJSONArray("members") ?: JSONArray()

                            val stateJson = JSONObject()
                                .put("members", membersArr)
                                .toString()

                            val locArr = JSONArray()
                            val abilitiesArr = JSONArray()

                            for (i in 0 until membersArr.length()) {
                                val m = membersArr.getJSONObject(i)
                                val id = m.optString("userId")
                                val name = m.optString("name", "")

                                val locObj = m.optJSONObject("loc")
                                if (locObj != null) {
                                    val locJson = JSONObject()
                                        .put("userId", id)
                                        .put("name", name)
                                        .put("lat", locObj.optDouble("lat"))
                                        .put("lon", locObj.optDouble("lon"))
                                        .put("ts", locObj.optLong("ts", 0L))
                                    locArr.put(locJson)
                                }

                                val memberAbilities = m.optJSONArray("abilities") ?: JSONArray()
                                for (j in 0 until memberAbilities.length()) {
                                    val a = memberAbilities.getJSONObject(j)
                                    val abilJson = JSONObject()
                                        .put("userId", id)
                                        .put("name", name)
                                        .put("abilityId", a.optString("id"))
                                        .put("ts", a.optLong("ts", 0L))
                                    abilitiesArr.put(abilJson)
                                }
                            }

                            prefs.edit()
                                .putString(PREF_ROOM_STATE_JSON, stateJson)
                                .putString(PREF_PLAYER_LOCATIONS, locArr.toString())
                                .putString(PREF_PLAYER_ABILITIES, abilitiesArr.toString())
                                .apply()
                        }
                    }
                } catch (_: Exception) {
                }
                try {
                    fetchSettingsOnce(baseUrl, code)
                } catch (_: Exception) {
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun fetchSettingsOnce(baseUrl: String, code: String) {
        val r = Request.Builder()
            .url("$baseUrl/rooms/$code/settings")
            .get()
            .build()
        client.newCall(r).execute().use { resp ->
            val txt = resp.body?.string().orEmpty()
            if (resp.isSuccessful && txt.isNotBlank()) {
                val trimmed = txt.trim()
                val settingsJsonString = if (trimmed.startsWith("[")) {
                    JSONObject().put("settings", JSONArray(trimmed)).toString()
                } else {
                    trimmed
                }
                updatingSettingsFromServer = true
                prefs.edit()
                    .putString(PREF_ROOM_SETTINGS_JSON, settingsJsonString)
                    .apply()
                updatingSettingsFromServer = false
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun sendStatusIfChanged(baseUrl: String, code: String, userId: String, token: String) {
        val teamRaw = prefs.getString(PREF_TEAM, null)
        val roleRaw = prefs.getString(PREF_ROLE, null)
        val hasHealth = prefs.contains(PREF_HEALTH)
        val health = if (hasHealth) prefs.getInt(PREF_HEALTH, 0) else null
        val team = teamRaw?.takeIf { it.isNotBlank() }
        val role = roleRaw?.takeIf { it.isNotBlank() }

        val shouldSend =
            team != lastTeamSent ||
                    role != lastRoleSent ||
                    health != lastHealthSent

        if (!shouldSend) return

        lastTeamSent = team
        lastRoleSent = role
        lastHealthSent = health

        scope.launch {
            try {
                val bodyObj = JSONObject().apply {
                    put("userId", userId)
                    put("token", token)
                    if (team != null) put("team", team)
                    if (role != null) put("role", role)
                    if (health != null) put("health", health)
                }
                val req = Request.Builder()
                    .url("$baseUrl/rooms/$code/status")
                    .post(bodyObj.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().close()
            } catch (_: Exception) {
            }
        }
    }

    private fun sendPendingAbilityIfAny(baseUrl: String, code: String, userId: String, token: String) {
        val abilityId = prefs.getString(PREF_PENDING_ABILITY_ID, null)?.trim()
        if (abilityId.isNullOrEmpty()) return
        scope.launch {
            try {
                val bodyObj = JSONObject().apply {
                    put("userId", userId)
                    put("token", token)
                    put("abilityId", abilityId)
                }
                val req = Request.Builder()
                    .url("$baseUrl/rooms/$code/ability")
                    .post(bodyObj.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        prefs.edit().remove(PREF_PENDING_ABILITY_ID).apply()
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun pushSettingsIfNeeded(baseUrl: String, code: String) {
        val json = prefs.getString(PREF_ROOM_SETTINGS_JSON, null)?.trim() ?: return
        if (json.isEmpty()) return
        scope.launch {
            try {
                val bodyStr = json
                val req = Request.Builder()
                    .url("$baseUrl/rooms/$code/settings")
                    .post(bodyStr.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().close()
            } catch (_: Exception) {
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == null) return

        if (key == PREF_BASE_URL || key == PREF_ROOM_CODE || key == PREF_USER_ID || key == PREF_TOKEN || key == PREF_SYNC_ENABLED) {
            startOrStopSyncFromPrefs()
        }
        if (key == PREF_TEAM || key == PREF_ROLE || key == PREF_HEALTH) {
            val base = prefs.getString(PREF_BASE_URL, null)
            val code = prefs.getString(PREF_ROOM_CODE, null)
            val uid = prefs.getString(PREF_USER_ID, null)
            val tok = prefs.getString(PREF_TOKEN, null)
            if (!base.isNullOrBlank() && !code.isNullOrBlank() && !uid.isNullOrBlank() && !tok.isNullOrBlank()) {
                sendStatusIfChanged(base, code, uid, tok)
            }
        }
        if (key == PREF_PENDING_ABILITY_ID) {
            val base = prefs.getString(PREF_BASE_URL, null)
            val code = prefs.getString(PREF_ROOM_CODE, null)
            val uid = prefs.getString(PREF_USER_ID, null)
            val tok = prefs.getString(PREF_TOKEN, null)
            if (!base.isNullOrBlank() && !code.isNullOrBlank() && !uid.isNullOrBlank() && !tok.isNullOrBlank()) {
                sendPendingAbilityIfAny(base, code, uid, tok)
            }
        }
        if (key == PREF_ROOM_SETTINGS_JSON && !updatingSettingsFromServer) {
            val base = prefs.getString(PREF_BASE_URL, null)
            val code = prefs.getString(PREF_ROOM_CODE, null)
            if (!base.isNullOrBlank() && !code.isNullOrBlank()) {
                pushSettingsIfNeeded(base, code)
            }
        }
    }
}
