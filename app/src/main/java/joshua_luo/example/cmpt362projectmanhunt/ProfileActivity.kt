package joshua_luo.example.cmpt362projectmanhunt

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

class ProfileActivity : AppCompatActivity() {

    private lateinit var profilePhoto: ImageView
    private lateinit var username: TextView
    private lateinit var achievements: RecyclerView
    private lateinit var matchHistory: RecyclerView
    private lateinit var editProfileBtn: Button
    private lateinit var exitBtn: Button

    private lateinit var db: AppDatabase
    private lateinit var statsDao: StatsDao
    private lateinit var statsRepository: StatsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // --- View binding ---
        profilePhoto = findViewById(R.id.profile_photo)
        username = findViewById(R.id.username)
        achievements = findViewById(R.id.achievement_recycler)
        matchHistory = findViewById(R.id.match_history_recycler)
        editProfileBtn = findViewById(R.id.edit_profile)
        exitBtn = findViewById(R.id.exit_profile)

        // --- DB + repository setup ---
        db = AppDatabase.getInstance(this)
        statsDao = db.statsDao()
        val client = OkHttpClient()
        // needed in StatsRepository
        val fakeBaseUrl = "https://example.com"
        statsRepository = StatsRepository(statsDao, client, fakeBaseUrl)

        // --- Profile photo from file (if exists) ---
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

        // --- Username from ProfilePrefs ---
        val profilePrefs = getSharedPreferences("ProfilePrefs", MODE_PRIVATE)
        val name = profilePrefs.getString("username_key", "")
        username.text = String.format("Username: %s", name)

        // --- RecyclerViews layout managers ---
        achievements.layoutManager = LinearLayoutManager(this)

        // Match history (dummy data for now)
        val matchList = listOf(
            MatchHistoryItem("Game001", "Runner", "Runner", 12.5),
            MatchHistoryItem("Game002", "Hunter", "Hunter", 8.3)
        )
        matchHistory.layoutManager = LinearLayoutManager(this)
        matchHistory.adapter = HistoryAdapter(matchList)

        // --- Buttons ---
        editProfileBtn.setOnClickListener {
            startActivity(Intent(this, ProfileSettingActivity::class.java))
        }

        exitBtn.setOnClickListener {
            finish()
        }

        // Load achievements & stats from DB
        updateAchievementsAndStatsUi()
    }

    private fun updateAchievementsAndStatsUi() {
        lifecycleScope.launch(Dispatchers.IO) {
            val stats = statsRepository.getStatsOrDefault()

            val achievementStrings = mutableListOf<String>()
            if (stats.totalGames >= 1) achievementStrings += "First Game"
            if (stats.totalWins >= 1) achievementStrings += "First Win"
            if (stats.totalDistanceMeters >= 5_000) achievementStrings += "Runner: 5km travelled"
            if (stats.totalTagsDone >= 10) achievementStrings += "Hunter Elite: 10 tags"

            withContext(Dispatchers.Main) {
                achievements.adapter = AchievementAdapter(achievementStrings)
            }
        }
    }
}
