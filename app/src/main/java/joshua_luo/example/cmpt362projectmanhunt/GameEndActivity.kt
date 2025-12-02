package joshua_luo.example.cmpt362projectmanhunt

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity

class GameEndActivity : ComponentActivity() {

    private lateinit var tvFinalTime: TextView
    private lateinit var tvYouTravelled: TextView
    private lateinit var btnMainMenu: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_end)

        tvFinalTime = findViewById(R.id.tvFinalTime )
        tvYouTravelled = findViewById(R.id.tvYouTravelled)
        btnMainMenu = findViewById(R.id.btnMainMenu)

        val finalTime = intent.getStringExtra("finalTime" ) ?: "0min 0sec"
        val distanceMeters = intent.getDoubleExtra("distanceMeters", 0.0)
        val isDead = intent.getBooleanExtra("isDead", false)

        tvFinalTime.text = finalTime

        val distanceKm = distanceMeters / 1000.0
        tvYouTravelled.text = String.format("%.2fkm", distanceKm )

        btnMainMenu.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }

        if (isDead) {
        }
    }
    override fun onBackPressed() {
        val isDead = intent.getBooleanExtra("isDead" , false)
        if (!isDead) {
            super.onBackPressed()
        }
    }
}
