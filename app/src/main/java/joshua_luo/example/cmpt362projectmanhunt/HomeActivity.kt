package joshua_luo.example.cmpt362projectmanhunt

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity

class HomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val btnPlay: Button = findViewById(R.id.btnPlay)
        val btnSettings: Button = findViewById(R.id.btnSettings)
        val btnLeaderboard: Button = findViewById(R.id.btnLeaderboard)
        val btnExit: Button = findViewById(R.id.btnExit)

        btnPlay.setOnClickListener {
            // Goes to the lobby creation page (MainActivity)
            startActivity(Intent(this, MainActivity::class.java))
        }

        // UI placeholders for later
        btnSettings.setOnClickListener {
            // Does nothing for now
        }
        btnLeaderboard.setOnClickListener {
            // Does nothing for now
        }

        // Exit app
        btnExit.setOnClickListener {
            finishAffinity()
        }
    }
}
