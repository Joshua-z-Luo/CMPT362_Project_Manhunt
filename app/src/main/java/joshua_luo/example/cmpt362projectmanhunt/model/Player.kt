package joshua_luo.example.cmpt362projectmanhunt.model

import java.net.URI

/**
 * Player data class
 */

data class Player(
    val id: Long,
    val photo: URI,
    val username: String,
    val role: Roles,
    val status: PlayerStatus,
    val position: Point,
    val timeCaptured: Long,
    val distanceTravelledM: Int,
    val playersCaptured: List<Long>
)
