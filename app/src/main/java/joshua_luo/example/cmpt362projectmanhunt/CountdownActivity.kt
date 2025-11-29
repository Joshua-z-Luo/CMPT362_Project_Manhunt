package joshua_luo.example.cmpt362projectmanhunt

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import androidx.activity.ComponentActivity

class CountdownActivity : ComponentActivity() {

    private lateinit var tvCountdown: TextView
    private var countdown: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_countdown)

        tvCountdown = findViewById(R.id.tvCountdown)

        //get data from intent
        val selectedHunterId = intent.getStringExtra("hunterId") ?: ""
        val currentUserId = intent.getStringExtra("userId") ?: ""
        val roomCode = intent.getStringExtra("roomCode") ?: ""
        val baseUrl = intent.getStringExtra("baseUrl" ) ?: ""
        val timerMinutes = intent.getIntExtra("timerMinutes", 30)
        val hunterRange = intent.getIntExtra("hunterRange", 50)
        val runnerRange = intent.getIntExtra("runnerRange", 100 )
        val abilityMode = intent.getBooleanExtra("abilityMode", false)

        startCountdown(
            selectedHunterId,
            currentUserId,
            roomCode,
            baseUrl,
            timerMinutes,
            hunterRange,
            runnerRange,
            abilityMode
        )
    }

    private fun startCountdown(
        hunterId: String,
        userId: String,
        roomCode: String,
        baseUrl: String,
        timerMinutes: Int,
        hunterRange: Int,
        runnerRange: Int,
        abilityMode: Boolean
    ) {
        countdown = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long ) {
                val seconds = (millisUntilFinished / 1000).toInt()
                tvCountdown.text = "Game starts in\n$seconds sec.."
            }

            override fun onFinish() {
                val isHunter = userId == hunterId

                if (isHunter) {
                    val intent = Intent(this@CountdownActivity, HunterGameActivity::class.java).apply {
                        putExtra("userId", userId)
                        putExtra("roomCode", roomCode)
                        putExtra("baseUrl", baseUrl)
                        putExtra("timerMinutes", timerMinutes)
                        putExtra("hunterRange", hunterRange)
                        putExtra("abilityMode", abilityMode)
                    }
                    startActivity(intent)
                } else {
                    val intent = Intent(this@CountdownActivity, RunnerGameActivity::class.java).apply {
                        putExtra("userId", userId)
                        putExtra("roomCode", roomCode)
                        putExtra("baseUrl", baseUrl)
                        putExtra("timerMinutes", timerMinutes)
                        putExtra("runnerRange", runnerRange)
                        putExtra("abilityMode", abilityMode)
                        putExtra("hunterId", hunterId)
                    }
                    startActivity(intent)
                }
                finish()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        countdown?.cancel()
    }
}