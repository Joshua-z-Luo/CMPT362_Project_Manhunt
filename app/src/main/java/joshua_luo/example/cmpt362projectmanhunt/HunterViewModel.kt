package joshua_luo.example.cmpt362projectmanhunt

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * HunterViewModel manages hunter-side abilities.
 */
class HunterViewModel : ViewModel() {

    private val _isRevealActive = MutableLiveData(false)
    val isRevealActive: LiveData<Boolean> = _isRevealActive

    private val _isRevealOnCooldown = MutableLiveData(false)
    val isRevealOnCooldown: LiveData<Boolean> = _isRevealOnCooldown

    private val _revealRemainingMillis = MutableLiveData(0L)
    val revealRemainingMillis: LiveData<Long> = _revealRemainingMillis

    private var revealJob: Job? = null

    // Might change
    private val revealDurationMs = 15_000L  // 15s of reveal
    private val revealCooldownMs = 30_000L  // 30s cooldown

    /**
     * Called when hunter taps the "Reveal" button.
     */
    fun useReveal() {
        if (_isRevealActive.value == true || _isRevealOnCooldown.value == true) return

        _isRevealActive.value = true
        _revealRemainingMillis.value = revealDurationMs

        revealJob?.cancel()
        revealJob = viewModelScope.launch {
            val start = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - start
                val remaining = revealDurationMs - elapsed
                if (remaining <= 0L) break
                _revealRemainingMillis.value = remaining
                delay(250L)
            }

            _isRevealActive.value = false
            _revealRemainingMillis.value = 0L

            _isRevealOnCooldown.value = true
            delay(revealCooldownMs)
            _isRevealOnCooldown.value = false
        }
    }
}
