package joshua_luo.example.cmpt362projectmanhunt

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import joshua_luo.example.cmpt362projectmanhunt.model.Ability
import joshua_luo.example.cmpt362projectmanhunt.model.PowerupType
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

    private val _currentAbility = MutableLiveData<Ability?>()
    val currentAbility: LiveData<Ability?> = _currentAbility

    private val _isAbilityActive = MutableLiveData(false)
    val isAbilityActive: LiveData<Boolean> = _isAbilityActive

    private val _isAbilityOnCooldown = MutableLiveData(false)
    val isAbilityOnCooldown: LiveData<Boolean> = _isAbilityOnCooldown

    private val _remainingDurationMillis = MutableLiveData(0L)
    val remainingDurationMillis: LiveData<Long> = _remainingDurationMillis

    private var timerJob: Job? = null

    /**
     * Assigns an ability to the runner.
     * Call this when the player picks up a power-up.
     */
    fun grantAbility(
        type: PowerupType,
        durationMs: Long,
        cooldownMs: Long
    ) {
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


    fun useAbility() {
        val ability = _currentAbility.value ?: return

        // Ignore if already active, on cooldown, or no real ability
        if (_isAbilityActive.value == true ||
            _isAbilityOnCooldown.value == true ||
            ability.type == PowerupType.None
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

            // Ability effect ends
            _isAbilityActive.value = false
            _remainingDurationMillis.value = 0L

            // Start cooldown
            if (ability.cooldownMillis > 0L) {
                _isAbilityOnCooldown.value = true
                delay(ability.cooldownMillis)
                _isAbilityOnCooldown.value = false
            }
        }
    }


    fun consumeShieldIfAvailable(): Boolean {
        val ability = _currentAbility.value
        val shieldActive = ability?.type == PowerupType.Shield && _isAbilityActive.value == true

        if (shieldActive) {
            // Shield absorbed one tag → turn it off
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
