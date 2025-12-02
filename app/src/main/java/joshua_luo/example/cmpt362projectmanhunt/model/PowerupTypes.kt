package joshua_luo.example.cmpt362projectmanhunt.model

/**
 * All ability types available in the game.
 * Some are hunter-only, some runner-only.
 */
enum class PowerupTypes {

    None,

    // --- Hunter abilities ---

    Reveal,            // Hunter reveals all runners temporarily
    Scan,              // Increase detection radius
    Compass,           // Show direction to nearest runner
    Tracker,           // Highlight / mark a random runner
    Trap,              // Trap zone that reveals runners entering it
    HunterInvisibility,// Runners can't see hunter location
    LockOn,            // Lock to first spotted runner, reveal after leaving radius

    // --- Runner abilities ---

    Invisibility,      // Runner invisibility (hunters can't see runner)
    Shield,            // Runner shield blocks one tag
    Hidden,            // Reduce hunter's reveal radius against this runner
    FlashBang,         // Temporarily white-out hunter's map
    Stationary         // Marker disappears when standing still for a few seconds
}
