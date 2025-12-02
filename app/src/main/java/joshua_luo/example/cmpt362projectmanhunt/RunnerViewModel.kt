package joshua_luo.example.cmpt362projectmanhunt

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import joshua_luo.example.cmpt362projectmanhunt.model.PowerupTypes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RunnerViewModel : ViewModel() {

    private val _invisibleActive = MutableLiveData(false)
    val invisibleActive: LiveData<Boolean> = _invisibleActive

    // 0.5f means hunter's reveal radius is halved for this runner
    private val _hiddenRadiusFactor = MutableLiveData(1.0f)
    val hiddenRadiusFactor: LiveData<Float> = _hiddenRadiusFactor

    private val _flashBangActive = MutableLiveData(false)
    val flashBangActive: LiveData<Boolean> = _flashBangActive

    private val _stationaryHideActive = MutableLiveData(false)
    val stationaryHideActive: LiveData<Boolean> = _stationaryHideActive

    private val _isCurrentlyHiddenByStationary = MutableLiveData(false)
    val isCurrentlyHiddenByStationary: LiveData<Boolean> = _isCurrentlyHiddenByStationary

    private val _shieldActive = MutableLiveData(false)
    val shieldActive: LiveData<Boolean> = _shieldActive

    private var invisJob: Job? = null
    private var hiddenJob: Job? = null
    private var flashJob: Job? = null
    private var stationaryJob: Job? = null
    private var shieldJob: Job? = null

    private var lastPosition: LatLng? = null
    private var lastMoveTimestamp: Long = System.currentTimeMillis()

    fun useAbility(type: PowerupTypes) {
        when (type) {
            PowerupTypes.Invisibility -> useInvisibility()
            PowerupTypes.Hidden -> useHidden()
            PowerupTypes.FlashBang -> useFlashBang()
            PowerupTypes.Stationary -> useStationary()
            PowerupTypes.Shield -> useShield()
            else -> { }
        }
    }

    private fun useInvisibility() {
        if (_invisibleActive.value == true) return

        _invisibleActive.value = true

        invisJob?.cancel()
        invisJob = viewModelScope.launch {
            delay(15_000L)
            _invisibleActive.postValue(false)
        }
    }

    private fun useHidden() {
        _hiddenRadiusFactor.value = 0.5f

        hiddenJob?.cancel()
        hiddenJob = viewModelScope.launch {
            delay(10_000L)
            _hiddenRadiusFactor.postValue(1.0f)
        }
    }

    private fun useFlashBang() {
        if (_flashBangActive.value == true) return

        _flashBangActive.value = true

        flashJob?.cancel()
        flashJob = viewModelScope.launch {
            delay(5_000L)
            _flashBangActive.postValue(false)
        }
    }

    private fun useStationary() {
        if (_stationaryHideActive.value == true) return

        _stationaryHideActive.value = true
        _isCurrentlyHiddenByStationary.value = false

        stationaryJob?.cancel()
        stationaryJob = viewModelScope.launch {
            val duration = 20_000L
            val start = System.currentTimeMillis()
            while (true) {
                val now = System.currentTimeMillis()
                val elapsed = now - start
                if (elapsed >= duration) break

                val idleTime = now - lastMoveTimestamp
                _isCurrentlyHiddenByStationary.postValue(idleTime >= 3_000L)

                delay(500L)
            }
            _stationaryHideActive.postValue(false)
            _isCurrentlyHiddenByStationary.postValue(false)
        }
    }

    private fun useShield() {
        if (_shieldActive.value == true) return

        _shieldActive.value = true

        shieldJob?.cancel()
        shieldJob = viewModelScope.launch {
            // Shield is available for one tag or until it times out
            delay(15_000L)
            _shieldActive.postValue(false)
        }
    }

    /**
     * Call this from game logic when a tag would happen.
     * Returns true if the shield blocked it.
     */
    fun consumeShieldIfAvailable(): Boolean {
        val active = _shieldActive.value == true
        if (active) {
            _shieldActive.value = false
            shieldJob?.cancel()
        }
        return active
    }

    fun updateRunnerPosition(pos: LatLng) {
        val last = lastPosition
        if (last == null || distanceMeters(last, pos) > 1.0) {
            lastPosition = pos
            lastMoveTimestamp = System.currentTimeMillis()
        }
    }

    private fun distanceMeters(a: LatLng, b: LatLng): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)

        val sinDLat = sin(dLat / 2)
        val sinDLon = sin(dLon / 2)

        val aa = sinDLat * sinDLat + cos(lat1) * cos(lat2) * sinDLon * sinDLon
        val c = 2 * atan2(sqrt(aa), sqrt(1 - aa))
        return R * c
    }
}
