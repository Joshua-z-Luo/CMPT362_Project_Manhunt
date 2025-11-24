package joshua_luo.example.cmpt362projectmanhunt

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class ProfileActivity: AppCompatActivity() {
    private lateinit var profilePhoto: ImageView
    private lateinit var username: TextView
    private lateinit var achievements: RecyclerView
    private lateinit var matchHistory: RecyclerView
    private lateinit var editProfileBtn: Button
    private lateinit var exitBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        profilePhoto = findViewById(R.id.profile_photo)
        username = findViewById(R.id.username)
        achievements = findViewById(R.id.achievement_recycler)
        matchHistory = findViewById(R.id.match_history_recycler)
        editProfileBtn = findViewById(R.id.edit_profile)
        exitBtn = findViewById(R.id.exit_profile)


        // Achievements
        // TODO: use actual data
        val achievementsList = listOf("First Win", "Top Hunter", "Top Runner")
        achievements.layoutManager = LinearLayoutManager(this)
        achievements.adapter = AchievementAdapter(achievementsList)

        // MatchHistory
        // TODO: use actual data
        val matchList = listOf(
            MatchHistoryItem("Game001", "Runner", "Runner", 12.5),
            MatchHistoryItem("Game002", "Hunter", "Hunter", 8.3),
            MatchHistoryItem("Game003", "Runner", "Hunter", 10.3),
            MatchHistoryItem("Game004", "Hunter", "Runner", 7.6),
            MatchHistoryItem("Game005", "Hunter", "Runner", 8.3),
        )
        matchHistory.layoutManager = LinearLayoutManager(this)
        matchHistory.adapter = HistoryAdapter(matchList)

        editProfileBtn.setOnClickListener {
            // go to profile settings
            startActivity(Intent(this, ProfileSettingActivity::class.java))
        }
        exitBtn.setOnClickListener {
            // exit profile
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        // Profile Photo
        val profileImgFile = File(getExternalFilesDir(null), "profile_photo.jpg")
        if (profileImgFile.exists()) {
            val imgUri = FileProvider.getUriForFile(
                this,
                "joshua_luo.example.cmpt362projectmanhunt",
                profileImgFile
            )
            val bitmap = Util.getBitmap(this, imgUri)
            profilePhoto.setImageBitmap(bitmap)
        }
        // Username
        val profilePrefs = getSharedPreferences("ProfilePrefs", MODE_PRIVATE)
        val name = profilePrefs.getString("username_key", "")
        username.text = String.format("Username: %s", name)

    }
}