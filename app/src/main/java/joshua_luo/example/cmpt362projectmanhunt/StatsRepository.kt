package joshua_luo.example.cmpt362projectmanhunt

import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores local stats, game history
 * Handles communication with the leaderboard backend.
 */
class StatsRepository(
    private val dao: StatsDao,
    private val client: OkHttpClient,
    private val baseUrl: String
) {

    fun observeRecentGames(limit: Int = 20): Flow<List<GameHistoryEntity>> =
        dao.getRecentGames(limit)

    suspend fun getStatsOrDefault(): PlayerStatsEntity {
        return dao.getStats() ?: PlayerStatsEntity()
    }

    /**
     * Saves the game record + updates aggregated stats.
     */
    suspend fun recordGame(result: GameHistoryEntity) {
        dao.insertGame(result)

        val current = dao.getStats() ?: PlayerStatsEntity()
        val updated = current.copy(
            totalGames = current.totalGames + 1,
            totalWins = current.totalWins + if (result.result == "Win") 1 else 0,
            totalDistanceMeters = current.totalDistanceMeters + result.distanceMeters,
            totalTimeHiddenMs = current.totalTimeHiddenMs + result.timeHiddenMs,
            totalTagsDone = current.totalTagsDone + result.tagsDone,
            totalTagsReceived = current.totalTagsReceived + result.tagsReceived
        )
        dao.insertStats(updated)
    }

    // ---------------- Leaderboard sync ----------------

    /**
     * Push local aggregated stats to the server so they
     * can be included in the global leaderboard.
     */
    suspend fun syncStatsToServer(userId: String, displayName: String) {
        val stats = getStatsOrDefault()

        val bodyJson = JSONObject().apply {
            put("userId", userId)
            put("name", displayName)
            put("totalGames", stats.totalGames)
            put("totalWins", stats.totalWins)
            put("totalDistanceMeters", stats.totalDistanceMeters)
            put("totalTimeHiddenMs", stats.totalTimeHiddenMs)
            put("totalTagsDone", stats.totalTagsDone)
            put("totalTagsReceived", stats.totalTagsReceived)
        }.toString()

        val req = Request.Builder()
            .url("$baseUrl/leaderboard/uploadStats")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                val current = getStatsOrDefault()
                dao.insertStats(current.copy(lastSyncAt = System.currentTimeMillis()))
            } else {
                // you can log / ignore failures for now
            }
        }
    }

    /**
     * Fetch the top players for the leaderboard screen.
     * Expected JSON format from backend:
     * [
     *   { "rank": 1, "name": "Alice", "totalDistanceMeters": 1234, "totalWins": 5 },
     *   ...
     * ]
     */
    suspend fun fetchLeaderboard(): List<LeaderboardEntry> {
        val req = Request.Builder()
            .url("$baseUrl/leaderboard/top")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string().orEmpty()
            if (!resp.isSuccessful || txt.isEmpty()) return emptyList()

            val arr = JSONArray(txt)
            val result = mutableListOf<LeaderboardEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                result += LeaderboardEntry(
                    rank = o.optInt("rank", i + 1),
                    name = o.optString("name", "Unknown"),
                    totalDistanceMeters = o.optLong("totalDistanceMeters", 0L),
                    totalWins = o.optInt("totalWins", 0)
                )
            }
            return result
        }
    }
}
