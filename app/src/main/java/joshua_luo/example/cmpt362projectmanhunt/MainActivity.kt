package joshua_luo.example.cmpt362projectmanhunt

import android.Manifest
import android.annotation.SuppressLint
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
    private lateinit var tvRoom: TextView
    private lateinit var tvUser: TextView
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
        tvRoom = findViewById(R.id.tvRoom)
        tvUser = findViewById(R.id.tvUser)
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

        renderStatus()
    }

    private fun renderStatus() {
        tvRoom.text = "Current Room: ${currentRoom ?: "-"}"
        tvUser.text = "Your ID: ${userId ?: "-"}"
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
