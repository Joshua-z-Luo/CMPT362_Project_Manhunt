package joshua_luo.example.cmpt362projectmanhunt

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per completed game.
 * Used for match history + more detailed achievement checks.
 */
@Entity(tableName = "game_history")
data class GameHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val finishedAt: Long,
    val role: String,               // "Hunter" or "Runner"
    val result: String,             // "Win", "Loss", "Escaped"
    val distanceMeters: Long,
    val timeHiddenMs: Long,
    val timeAsHunterMs: Long,
    val tagsDone: Int,
    val tagsReceived: Int
)
