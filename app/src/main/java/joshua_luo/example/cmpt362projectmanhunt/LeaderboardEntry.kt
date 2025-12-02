package joshua_luo.example.cmpt362projectmanhunt

/**
 * One row in the leaderboard.
 */
data class LeaderboardEntry(
    val rank: Int,
    val name: String,
    val totalDistanceMeters: Long,
    val totalWins: Int
)
