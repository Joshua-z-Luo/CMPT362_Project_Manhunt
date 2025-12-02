package joshua_luo.example.cmpt362projectmanhunt

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CaughtActivity : ComponentActivity() {

    private val client by lazy { OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caught)

        val btnSpectate: Button = findViewById(R.id.btnSpectate)
        val btnBecomeHunter: Button = findViewById(R.id.btnBecomeHunter)
        val btnExitToLobby: Button = findViewById(R.id.btnExitToLobby)

        val userId =intent.getStringExtra("userId")
        val token = intent.getStringExtra("token")
        val roomCode = intent.getStringExtra("roomCode")
        val baseUrl = intent.getStringExtra("baseUrl")
        val distanceMeters = intent.getDoubleExtra("distanceMeters", 0.0)
        val finalTime = intent.getStringExtra("finalTime") ?: "0min 0sec"

        //set health to 0 when caught
        if (userId != null && token != null && roomCode != null && baseUrl != null ) {
            updateHealthToZero(userId, token, roomCode, baseUrl )
        }


        btnExitToLobby.setOnClickListener {
            val intent = Intent(this, GameEndActivity::class.java)
            intent.putExtra("finalTime", finalTime)
            intent.putExtra("isHunter", false)
            intent.putExtra("distanceMeters", distanceMeters)
            startActivity(intent)
            finish()
        }
    }

    private fun updateHealthToZero(userId: String, token: String, roomCode: String, baseUrl: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("userId", userId)
                    put("token", token)
                    put("health", 0)
                }.toString()

                val request = Request.Builder()
                    .url("$baseUrl/rooms/$roomCode/status" )
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
