package joshua_luo.example.cmpt362projectmanhunt

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity

class CaughtActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caught)

        val btnSpectate: Button = findViewById(R.id.btnSpectate)
        val btnBecomeHunter: Button = findViewById(R.id.btnBecomeHunter)
        val btnExitToLobby: Button = findViewById(R.id.btnExitToLobby)

        btnSpectate.setOnClickListener {
            // TODO: Implement spectate mode
            Toast.makeText(this, "Spectate mode - Coming soon!", Toast.LENGTH_SHORT).show()
        }

        btnBecomeHunter.setOnClickListener {
            // TODO: Implement become hunter functionality
            Toast.makeText(this, "Become Hunter - Coming soon!", Toast.LENGTH_SHORT).show()
        }

        btnExitToLobby.setOnClickListener {
            // TODO: Return to lobby or home screen
            Toast.makeText(this, "Exit to Lobby - Coming soon!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}