package joshua_luo.example.cmpt362projectmanhunt

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.w3c.dom.Text

class HelpActivity: AppCompatActivity() {
    private lateinit var gameRulesHeader: TextView
    private lateinit var hunterRulesHeader: TextView
    private lateinit var runnerRulesHeader: TextView
    private lateinit var gameModeHeader: TextView
    private lateinit var hunterAbilityHeader: TextView
    private lateinit var runnerAbilityHeader: TextView
    private lateinit var gameRulesContent: LinearLayout
    private lateinit var hunterRulesContent: LinearLayout
    private lateinit var runnerRulesContent: LinearLayout
    private lateinit var gameModeContent: LinearLayout
    private lateinit var hunterAbilityContent: LinearLayout
    private lateinit var runnerAbilityContent: LinearLayout

    private lateinit var exitBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        gameRulesHeader = findViewById(R.id.game_rules_header)
        hunterRulesHeader = findViewById(R.id.hunter_rules_header)
        runnerRulesHeader = findViewById(R.id.runner_rules_header)
        gameModeHeader = findViewById(R.id.game_modes_header)
        hunterAbilityHeader = findViewById(R.id.hunter_abilities_header)
        runnerAbilityHeader = findViewById(R.id.runner_abilities_header)

        gameRulesContent = findViewById(R.id.game_rules)
        hunterRulesContent = findViewById(R.id.hunter_rules)
        runnerRulesContent = findViewById(R.id.runner_rules)
        gameModeContent = findViewById(R.id.game_modes)
        hunterAbilityContent = findViewById(R.id.hunter_abilities)
        runnerAbilityContent = findViewById(R.id.runner_abilities)

        exitBtn = findViewById(R.id.btnExit)

        gameRulesHeader.setOnClickListener {
            if (gameRulesContent.visibility == View.GONE) {
                gameRulesContent.visibility = View.VISIBLE
            } else {
                gameRulesContent.visibility = View.GONE
            }
        }
        gameModeHeader.setOnClickListener {
            if (gameModeContent.visibility == View.GONE) {
                gameModeContent.visibility = View.VISIBLE
            } else {
                gameModeContent.visibility = View.GONE
            }
        }
        hunterRulesHeader.setOnClickListener {
            if (hunterRulesContent.visibility == View.GONE) {
                hunterRulesContent.visibility = View.VISIBLE
            } else {
                hunterRulesContent.visibility = View.GONE
            }
        }
        runnerRulesHeader.setOnClickListener {
            if (runnerRulesContent.visibility == View.GONE) {
                runnerRulesContent.visibility = View.VISIBLE
            } else {
                runnerRulesContent.visibility = View.GONE
            }
        }
        hunterAbilityHeader.setOnClickListener {
            if (hunterAbilityContent.visibility == View.GONE) {
                hunterAbilityContent.visibility = View.VISIBLE
            } else {
                hunterAbilityContent.visibility = View.GONE
            }
        }
        runnerAbilityHeader.setOnClickListener {
            if (runnerAbilityContent.visibility == View.GONE) {
                runnerAbilityContent.visibility = View.VISIBLE
            } else {
                runnerAbilityContent.visibility = View.GONE
            }
        }
        exitBtn.setOnClickListener {
            finish()
        }
    }

}