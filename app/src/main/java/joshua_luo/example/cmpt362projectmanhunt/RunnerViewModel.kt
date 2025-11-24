package joshua_luo.example.cmpt362projectmanhunt

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import joshua_luo.example.cmpt362projectmanhunt.model.Ability
import joshua_luo.example.cmpt362projectmanhunt.model.PowerupTypes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * RunnerViewModel focuses on the runner’s point of view.
 *
 * It manages:
 *  - which ability the runner currently has
 *  - whether the ability is active or on cooldown
 *  - the remaining time until the ability expires
 *
 * The View (Runner's screen) just observes the LiveData
 * and calls useAbility() when the button is tapped.
 */
class RunnerViewModel : ViewModel() {

    private val _currentAbility = MutableLiveData<Ability?>(null)
    val currentAbility: LiveData<Ability?> = _currentAbility

    private val _isAbilityActive = MutableLiveData(false)
    val isAbilityActive: LiveData<Boolean> = _isAbilityActive

    private val _isAbilityOnCooldown = MutableLiveData(false)
    val isOnCooldown: LiveData<Boolean> = _isAbilityOnCooldown

    private val _remainingDurationMillis = MutableLiveData(0L)
    val remainingDurationMillis: LiveData<Long> = _remainingDurationMillis

    private var timerJob: Job? = null

    /**
     * Give the runner a new ability.
     */
    fun grantAbility(type: PowerupTypes, durationMs: Long, cooldownMs: Long) {
        _currentAbility.value = Ability(
            id = System.currentTimeMillis(),
            type = type,
            location = null,
            isActive = false,
            durationMillis = durationMs,
            cooldownMillis = cooldownMs,
            appliedToPlayerId = null,
            timeUsedMillis = null
        )
        _isAbilityActive.value = false
        _isAbilityOnCooldown.value = false
        _remainingDurationMillis.value = 0L
    }

    /**
     * Called when the runner taps "Use Ability".
     */
    fun useAbility() {
        val ability = _currentAbility.value ?: return

        // Ignore if already active, on cooldown, or no real ability
        if (_isAbilityActive.value == true ||
            _isAbilityOnCooldown.value == true ||
            ability.type == PowerupTypes.None
        ) return

        _isAbilityActive.value = true
        _currentAbility.value = ability.copy(
            isActive = true,
            timeUsedMillis = System.currentTimeMillis()
        )

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val duration = ability.durationMillis
            if (duration > 0L) {
                val start = System.currentTimeMillis()
                while (true) {
                    val elapsed = System.currentTimeMillis() - start
                    val remaining = duration - elapsed
                    if (remaining <= 0L) break
                    _remainingDurationMillis.value = remaining
                    delay(250L)
                }
            }

            // effect ends
            _isAbilityActive.value = false
            _remainingDurationMillis.value = 0L

            // cooldown
            if (ability.cooldownMillis > 0L) {
                _isAbilityOnCooldown.value = true
                delay(ability.cooldownMillis)
                _isAbilityOnCooldown.value = false
            }
        }
    }

    /**
     * @return true if Invisibility is currently active.
     */
    fun isInvisibleNow(): Boolean {
        val ability = _currentAbility.value
        return ability?.type == PowerupTypes.Invisibility && _isAbilityActive.value == true
    }

    /**
     * Consume Shield if it is active.
     * @return true if the shield blocked the tag.
     */
    fun consumeShieldIfAvailable(): Boolean {
        val ability = _currentAbility.value
        val shieldActive = ability?.type == PowerupTypes.Shield && _isAbilityActive.value == true

        if (shieldActive) {
            _isAbilityActive.value = false
            _currentAbility.value = ability.copy(
                isActive = false,
                durationMillis = 0L,
                cooldownMillis = 0L
            )
        }

        return shieldActive
    }
}
