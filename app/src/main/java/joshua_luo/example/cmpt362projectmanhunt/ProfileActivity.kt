package joshua_luo.example.cmpt362projectmanhunt

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var viewModel: ProfileViewModel

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

        // --- History adapter setup ---
        historyAdapter = HistoryAdapter(mutableListOf())
        matchHistory.adapter = historyAdapter

        // --- ProfileViewModel ---
        viewModel = ViewModelProvider(
            this,
            ProfileViewModel.Factory(statsRepository)
        )[ProfileViewModel::class.java]
        viewModel.load()

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
        } else {
            profilePhoto.setImageResource(R.drawable.default_profile)
        }

        // --- Username from ProfilePrefs ---
        val profilePrefs = getSharedPreferences("ProfilePrefs", MODE_PRIVATE)
        val name = profilePrefs.getString("username_key", "")
        username.text = String.format("Username: %s", name)

        // --- RecyclerViews layout managers ---
        // --- Achievements ---
        achievements.layoutManager = LinearLayoutManager(this)

        // Match history (dummy data for now)
        // val matchList = listOf(
        // MatchHistoryItem("Game001", "Runner", "Runner", 12.5),
        // MatchHistoryItem("Game002", "Hunter", "Hunter", 8.3)
        // )
        matchHistory.layoutManager = LinearLayoutManager(this)

        // --- Buttons ---
        editProfileBtn.setOnClickListener {
            startActivity(Intent(this, ProfileSettingActivity::class.java))
        }

        exitBtn.setOnClickListener {
            finish()
        }

        // testing
        // insertFakeStats()
        // insertFakeGameHistory()

        // Load achievements & stats from DB
        updateAchievementsAndStatsUi()

        // Load history
        updateHistoryUi()
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
    private fun updateHistoryUi() {
        viewModel.gameHistory.observe(this) { history ->
            val mapped = history.map { game ->
                MatchHistoryItem(
                    gameId = game.id.toString(),
                    winner = game.result,
                    role = game.role,
                    duration = game.timeHiddenMs / 1000.0
                )
            }

            historyAdapter.update(mapped)
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

    // Testing achievements using stats
//    private fun insertFakeStats() {
//        lifecycleScope.launch(Dispatchers.IO) {
//            val fakeStats = PlayerStatsEntity(
//                totalGames = 3,           // triggers "First Game"
//                totalWins = 2,            // triggers "First Win"
//                totalDistanceMeters = 6000, // triggers "Runner: 5km travelled"
//                totalTimeHiddenMs = 15_000,
//                totalTagsDone = 12,       // triggers "Hunter Elite: 10 tags"
//                totalTagsReceived = 5
//            )
//            statsDao.insertStats(fakeStats)
//
//            withContext(Dispatchers.Main) {
//                updateAchievementsAndStatsUi()
//            }
//        }
//    }

    // Test game history
//    private fun insertFakeGameHistory() {
//        lifecycleScope.launch(Dispatchers.IO) {
//            val fakeGames = listOf(
//                GameHistoryEntity(
//                    finishedAt = System.currentTimeMillis(),
//                    role = "Runner",
//                    result = "Win",
//                    distanceMeters = 3000,
//                    timeHiddenMs = 5000,
//                    timeAsHunterMs = 0,
//                    tagsDone = 0,
//                    tagsReceived = 1
//                )
//            )
//
//            fakeGames.forEach { statsDao.insertGame(it) }
//
//            withContext(Dispatchers.Main) {
//                updateHistoryUi()
//            }
//        }
//    }

}
