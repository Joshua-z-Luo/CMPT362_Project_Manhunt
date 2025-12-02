package joshua_luo.example.cmpt362projectmanhunt

import android.os.Bundle
import android.text.Html
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
    private lateinit var hunterAbilityHeader: TextView
    private lateinit var runnerAbilityHeader: TextView
    private lateinit var achievementHeader: TextView


    private lateinit var gameRulesContent: LinearLayout
    private lateinit var hunterRulesContent: LinearLayout
    private lateinit var runnerRulesContent: LinearLayout
    private lateinit var hunterAbilityContent: LinearLayout
    private lateinit var runnerAbilityContent: LinearLayout
    private lateinit var achievementContent: LinearLayout


    private lateinit var gameRulesText: TextView
    private lateinit var hunterRulesText: TextView
    private lateinit var runnerRulesText: TextView
    private lateinit var hunterAbilityText: TextView
    private lateinit var runnerAbilityText: TextView
    private lateinit var achievementText: TextView


    private lateinit var exitBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        gameRulesHeader = findViewById(R.id.game_rules_header)
        hunterRulesHeader = findViewById(R.id.hunter_rules_header)
        runnerRulesHeader = findViewById(R.id.runner_rules_header)
        hunterAbilityHeader = findViewById(R.id.hunter_abilities_header)
        runnerAbilityHeader = findViewById(R.id.runner_abilities_header)
        achievementHeader = findViewById(R.id.achievement_header)

        gameRulesContent = findViewById(R.id.game_rules)
        hunterRulesContent = findViewById(R.id.hunter_rules)
        runnerRulesContent = findViewById(R.id.runner_rules)
        hunterAbilityContent = findViewById(R.id.hunter_abilities)
        runnerAbilityContent = findViewById(R.id.runner_abilities)
        achievementContent = findViewById(R.id.achievements)

        gameRulesText = findViewById(R.id.game_rule_text)
        hunterRulesText = findViewById(R.id.hunter_rules_text)
        runnerRulesText = findViewById(R.id.runner_rules_text)
        hunterAbilityText = findViewById(R.id.hunter_abilities_text)
        runnerAbilityText = findViewById(R.id.runner_abilities_text)

        exitBtn = findViewById(R.id.btnExit)

        gameRulesHeader.setOnClickListener {
            if (gameRulesContent.visibility == View.GONE) {
                gameRulesContent.visibility = View.VISIBLE
            } else {
                gameRulesContent.visibility = View.GONE
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
        achievementHeader.setOnClickListener {
            if (achievementContent.visibility == View.GONE) {
                achievementContent.visibility = View.VISIBLE
            } else {
                achievementContent.visibility = View.GONE
            }
        }
        exitBtn.setOnClickListener {
            finish()
        }
    }

}