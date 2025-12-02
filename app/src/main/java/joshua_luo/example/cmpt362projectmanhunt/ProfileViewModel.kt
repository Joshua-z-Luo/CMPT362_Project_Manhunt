package joshua_luo.example.cmpt362projectmanhunt

import android.graphics.Bitmap
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * ViewModel for ProfileActivity.
 * Shows:
 *  - aggregated stats
 *  - recent game history
 *  - unlocked achievements
 */

class ProfileViewModel(private val statsRepository: StatsRepository): ViewModel() {

    // Moved to ProfileSettingsViewModel
    // val userImage = MutableLiveData<Bitmap>()


    private val _stats = MutableLiveData<PlayerStatsEntity>()
    val stats: LiveData<PlayerStatsEntity> = _stats

    val gameHistory: LiveData<List<GameHistoryEntity>> =
        statsRepository.observeRecentGames().asLiveData()

    private val _achievements = MutableLiveData<List<AchievementUi>>()
    val achievements: LiveData<List<AchievementUi>> = _achievements

    fun load() {
        viewModelScope.launch {
            val s = statsRepository.getStatsOrDefault()
            _stats.value = s
            _achievements.value = calculateAchievements(s)
        }
    }

    private fun calculateAchievements(stats: PlayerStatsEntity): List<AchievementUi> {
        val list = mutableListOf<AchievementUi>()

        if (stats.totalGames >= 1) {
            list += AchievementUi("First Game", "Finish your first match")
        }
        if (stats.totalWins >= 1) {
            list += AchievementUi("First Win", "Win your first game")
        }
        if (stats.totalDistanceMeters >= 5_000) {
            list += AchievementUi("Runner", "Travel 5 km in total")
        }
        if (stats.totalTimeHiddenMs >= 10 * 60 * 1000L) {
            list += AchievementUi("Stealth Master", "Stay hidden for 10 minutes total")
        }
        if (stats.totalTagsDone >= 10) {
            list += AchievementUi("Elite Hunter", "Tag 10 runners")
        }

        return list
    }

    data class AchievementUi(
        val title: String,
        val description: String
    )

    class Factory(
        private val statsRepository: StatsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProfileViewModel(statsRepository) as T
        }
    }
}