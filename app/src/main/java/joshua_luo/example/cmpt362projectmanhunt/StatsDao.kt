package joshua_luo.example.cmpt362projectmanhunt

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {

    @Query("SELECT * FROM player_stats WHERE id = 1")
    suspend fun getStats(): PlayerStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(stats: PlayerStatsEntity)

    @Insert
    suspend fun insertGame(history: GameHistoryEntity)

    @Query("SELECT * FROM game_history ORDER BY finishedAt DESC LIMIT :limit")
    fun getRecentGames(limit: Int = 20): Flow<List<GameHistoryEntity>>
}
