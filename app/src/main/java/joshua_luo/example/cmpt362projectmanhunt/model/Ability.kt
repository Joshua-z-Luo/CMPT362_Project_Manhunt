package joshua_luo.example.cmpt362projectmanhunt.model

/**
 * Ability / power-up model
 * This represents a single ability instance in the game.
 */
data class Ability(
    val id: Long,
    val type: PowerupTypes,
    val location: Point? = null,
    val isActive: Boolean = false,
    val durationMillis: Long,
    val cooldownMillis: Long,
    val appliedToPlayerId: Long? = null,
    val timeUsedMillis: Long? = null
)
