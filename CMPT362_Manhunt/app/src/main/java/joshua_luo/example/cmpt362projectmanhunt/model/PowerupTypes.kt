package joshua_luo.example.cmpt362projectmanhunt.model

/**
 * All ability types available in the game.
 * Some are hunter-only, some runner-only.
 */
enum class PowerupTypes(val id: String) {

    // --- Hunter abilities ---

    Reveal("reveal"),            // Hunter reveals all runners temporarily
    Scan("scan"),              // Increase detection radius
    Compass("compass"),           // Show direction to nearest runner
    Tracker("tracker"),           // Highlight / mark a random runner
    Trap("trap"),              // Trap zone that reveals runners entering it
    HunterInvisibility("hunterInvisibility"),// Runners can't see hunter location
    LockOn("lockOn"),            // Lock to first spotted runner, reveal after leaving radius

    // --- Runner abilities ---

    Invisibility("invisibility"),      // Runner invisibility (hunters can't see runner)
    Shield("shield"),            // Runner shield blocks one tag
    Hidden("hidden"),            // Reduce hunter's reveal radius against this runner
    FlashBang("flashBang"),         // Temporarily white-out hunter's map
    Stationary("stationary")         // Marker disappears when standing still for a few seconds
}
