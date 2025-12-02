package joshua_luo.example.cmpt362projectmanhunt

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.ComponentActivity

class SettingsActivity : ComponentActivity() {

    private lateinit var spinnerHunterSelection: Spinner
    private lateinit var etHunterRange: EditText
    private lateinit var etRunnerRange: EditText
    private lateinit var spinnerAbilityMode: Spinner
    private lateinit var spinnerTimer: Spinner
    private lateinit var etCustomTimer: EditText
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        spinnerHunterSelection = findViewById(R.id.spinnerHunterSelection)
        etHunterRange = findViewById(R.id.etHunterRange)
        etRunnerRange = findViewById(R.id.etRunnerRange)
        spinnerAbilityMode = findViewById(R.id.spinnerAbilityMode)
        spinnerTimer = findViewById(R.id.spinnerTimer)
        etCustomTimer = findViewById(R.id.etCustomTimer)
        btnSave = findViewById(R.id.btnSave)

        setupSpinners()
        loadSettings()

        btnSave.setOnClickListener {
            saveSettings()
            finish()
        }
    }

    private fun setupSpinners() {
        //hunter Selection
        val hunterOptions = arrayOf("Random")
        val hunterAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, hunterOptions)
        hunterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerHunterSelection.adapter = hunterAdapter

        //ability Mode Spinner
        val abilityOptions = arrayOf("Off", "On")
        val abilityAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, abilityOptions)
        abilityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerAbilityMode.adapter = abilityAdapter

        //timer
        val timerOptions = arrayOf("5 min", "15 min", "30 min", "45 min", "Other")
        val timerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, timerOptions)
        timerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item )
        spinnerTimer.adapter = timerAdapter

        //custom timer
        spinnerTimer.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (timerOptions[position] == "Other") {
                    etCustomTimer.visibility = View.VISIBLE
                } else {
                    etCustomTimer.visibility = View.GONE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                etCustomTimer.visibility = View.GONE
            }
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("GameSettings", MODE_PRIVATE)

        etHunterRange.setText(prefs.getString("hunterRange", "50"))
        etRunnerRange.setText(prefs.getString("runnerRange", "100"))

        spinnerHunterSelection.setSelection(prefs.getInt("hunterSelection", 0))

        spinnerAbilityMode.setSelection(prefs.getInt("abilityMode", 0))

        val timerSelection = prefs.getInt("timerSelection", 2)
        spinnerTimer.setSelection(timerSelection)

        //custom timer value
        if (timerSelection == 4) {
            etCustomTimer.setText(prefs.getString("customTimer", ""))
            etCustomTimer.visibility = View.VISIBLE
        }
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("GameSettings", MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putString("hunterRange", etHunterRange.text.toString())
        editor.putString("runnerRange", etRunnerRange.text.toString())
        editor.putInt("hunterSelection", spinnerHunterSelection.selectedItemPosition)
        editor.putInt("abilityMode", spinnerAbilityMode.selectedItemPosition)
        editor.putInt("timerSelection", spinnerTimer.selectedItemPosition)

        // Save custom timer value if "Other" is selected
        if (spinnerTimer.selectedItemPosition == 4) {
            editor.putString("customTimer", etCustomTimer.text.toString())
        }

        editor.apply()
        Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()
    }

    companion object {

        fun getTimerMinutes(context: Context): Int {
            val prefs = context.getSharedPreferences("GameSettings", MODE_PRIVATE)
            val timerSelection = prefs.getInt("timerSelection", 2) // default 30 min
            return when (timerSelection) {
                0 -> 5
                1 -> 15
                2 -> 30
                3 -> 45
                4 -> prefs.getString("customTimer", "30")?.toIntOrNull() ?: 30
                else -> 30
            }
        }

        fun getHunterRange(context: Context): Int {
            val prefs = context.getSharedPreferences("GameSettings", MODE_PRIVATE)
            return prefs.getString("hunterRange", "50")?.toIntOrNull() ?: 50
        }

        fun getRunnerRange(context: Context): Int {
            val prefs = context.getSharedPreferences("GameSettings", MODE_PRIVATE)
            return prefs.getString("runnerRange", "100" )?.toIntOrNull() ?: 100
        }

        fun isAbilityModeEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences("GameSettings", MODE_PRIVATE)
            return prefs.getInt("abilityMode", 0) == 1
        }
    }
}