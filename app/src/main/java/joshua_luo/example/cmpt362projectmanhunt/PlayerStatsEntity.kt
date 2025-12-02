package joshua_luo.example.cmpt362projectmanhunt

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Aggregated stats for a single player/device.
 */
@Entity(tableName = "player_stats")
data class PlayerStatsEntity(
    @PrimaryKey val id: Long = 1L,
    val totalGames: Int = 0,
    val totalWins: Int = 0,
    val totalDistanceMeters: Long = 0L,
    val totalTimeHiddenMs: Long = 0L,
    val totalTagsDone: Int = 0,
    val totalTagsReceived: Int = 0,
    val lastSyncAt: Long = 0L
)
