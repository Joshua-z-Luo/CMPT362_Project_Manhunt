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

    private val _hiddenRadiusFactor = MutableLiveData(1.0f)
    val hiddenRadiusFactor: LiveData<Float> = _hiddenRadiusFactor

    private val _stationaryActive = MutableLiveData(false)
    val stationaryActive: LiveData<Boolean> = _stationaryActive

    private val _shieldCharges = MutableLiveData(0)
    val shieldCharges: LiveData<Int> = _shieldCharges

    private var invisJob: Job? = null
    private var hiddenJob: Job? = null
    private var stationaryJob: Job? = null

    private var lastPosition: LatLng? = null
    private var lastMoveTimestamp: Long = System.currentTimeMillis()

    fun useAbility(type: PowerupTypes) {
        when (type) {
            PowerupTypes.Invisibility -> useInvisibility()
            PowerupTypes.Hidden -> useHidden()
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

    private fun useStationary() {
        if (_stationaryActive.value == true) return
        _stationaryActive.value = true
        stationaryJob?.cancel()
        stationaryJob = viewModelScope.launch {
            delay(20_000L)
            _stationaryActive.postValue(false)
        }
    }

    private fun useShield() {
        val current = _shieldCharges.value ?: 0
        if (current >= 1) return
        _shieldCharges.value = 1
    }

    fun consumeShieldIfActive(): Boolean {
        val current = _shieldCharges.value ?: 0
        if (current <= 0) return false
        _shieldCharges.value = current - 1
        return true
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
