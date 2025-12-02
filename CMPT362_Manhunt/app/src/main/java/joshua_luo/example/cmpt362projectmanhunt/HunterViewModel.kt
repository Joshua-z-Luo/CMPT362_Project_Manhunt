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

class HunterViewModel : ViewModel() {

    private val _scanRangeMultiplier = MutableLiveData(1.0f)
    val scanRangeMultiplier: LiveData<Float> = _scanRangeMultiplier

    private val _revealActive = MutableLiveData(false)
    val revealActive: LiveData<Boolean> = _revealActive

    private val _trackedRunnerId = MutableLiveData<String?>(null)
    val trackedRunnerId: LiveData<String?> = _trackedRunnerId

    private val _hunterInvisibleActive = MutableLiveData(false)
    val hunterInvisibleActive: LiveData<Boolean> = _hunterInvisibleActive

    private var scanJob: Job? = null
    private var revealJob: Job? = null
    private var trackerJob: Job? = null
    private var invisJob: Job? = null

    private var hunterPosition: LatLng? = null
    private var runnerPositions: Map<String, LatLng> = emptyMap()

    fun useAbility(type: PowerupTypes) {
        when (type) {
            PowerupTypes.Scan -> useScan()
            PowerupTypes.Reveal -> useReveal()
            PowerupTypes.Tracker -> useTracker()
            PowerupTypes.HunterInvisibility -> useHunterInvisibility()
            else -> { }
        }
    }

    fun updateHunterPosition(pos: LatLng) {
        hunterPosition = pos
    }

    fun updateRunnerPositions(positions: Map<String, LatLng>) {
        runnerPositions = positions
    }

    private fun useScan() {
        if (_scanRangeMultiplier.value ?: 1.0f > 1.0f) return
        _scanRangeMultiplier.value = 1.5f
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            delay(10_000L)
            _scanRangeMultiplier.postValue(1.0f)
        }
    }

    private fun useReveal() {
        if (_revealActive.value == true) return
        _revealActive.value = true
        revealJob?.cancel()
        revealJob = viewModelScope.launch {
            delay(8_000L)
            _revealActive.postValue(false)
        }
    }

    private fun useTracker() {
        if (_trackedRunnerId.value != null || runnerPositions.isEmpty()) return
        val randomId = runnerPositions.keys.random()
        _trackedRunnerId.value = randomId
        trackerJob?.cancel()
        trackerJob = viewModelScope.launch {
            delay(20_000L)
            _trackedRunnerId.postValue(null)
        }
    }

    private fun useHunterInvisibility() {
        if (_hunterInvisibleActive.value == true) return
        _hunterInvisibleActive.value = true
        invisJob?.cancel()
        invisJob = viewModelScope.launch {
            delay(15_000L)
            _hunterInvisibleActive.postValue(false)
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
