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

    private val _scanActive = MutableLiveData(false)
    val scanActive: LiveData<Boolean> = _scanActive

    private val _scanRangeMultiplier = MutableLiveData(1.0f)
    val scanRangeMultiplier: LiveData<Float> = _scanRangeMultiplier

    private val _compassActive = MutableLiveData(false)
    val compassActive: LiveData<Boolean> = _compassActive

    private val _compassRemainingMillis = MutableLiveData(0L)
    val compassRemainingMillis: LiveData<Long> = _compassRemainingMillis

    private val _compassBearingDeg = MutableLiveData<Float?>(null)
    val compassBearingDeg: LiveData<Float?> = _compassBearingDeg

    private val _trackedRunnerId = MutableLiveData<String?>(null)
    val trackedRunnerId: LiveData<String?> = _trackedRunnerId

    private val _trapCenter = MutableLiveData<LatLng?>(null)
    val trapCenter: LiveData<LatLng?> = _trapCenter

    private val _trapRadiusMeters = MutableLiveData(0.0)
    val trapRadiusMeters: LiveData<Double> = _trapRadiusMeters

    private val _trapTriggeredRunnerId = MutableLiveData<String?>(null)
    val trapTriggeredRunnerId: LiveData<String?> = _trapTriggeredRunnerId

    private val _hunterInvisibleActive = MutableLiveData(false)
    val hunterInvisibleActive: LiveData<Boolean> = _hunterInvisibleActive

    private val _lockOnRunnerId = MutableLiveData<String?>(null)
    val lockOnRunnerId: LiveData<String?> = _lockOnRunnerId

    private val _lockOnRemainingMillis = MutableLiveData(0L)
    val lockOnRemainingMillis: LiveData<Long> = _lockOnRemainingMillis

    private val _revealActive = MutableLiveData(false)
    val revealActive: LiveData<Boolean> = _revealActive

    private val _revealOnCooldown = MutableLiveData(false)
    val revealOnCooldown: LiveData<Boolean> = _revealOnCooldown

    private val _revealRemainingMillis = MutableLiveData(0L)
    val revealRemainingMillis: LiveData<Long> = _revealRemainingMillis

    private var scanJob: Job? = null
    private var compassJob: Job? = null
    private var trackerJob: Job? = null
    private var invisJob: Job? = null
    private var lockOnJob: Job? = null
    private var revealJob: Job? = null

    private var hunterPosition: LatLng? = null
    private var runnerPositions: Map<String, LatLng> = emptyMap()

    fun useAbility(type: PowerupTypes) {
        when (type) {
            PowerupTypes.Scan -> useScan()
            PowerupTypes.Compass -> useCompass()
            PowerupTypes.Tracker -> useTracker()
            PowerupTypes.Trap -> { }
            PowerupTypes.HunterInvisibility -> useHunterInvisibility()
            PowerupTypes.LockOn -> startLockOnMode()
            PowerupTypes.Reveal -> useReveal()
            else -> { }
        }
    }

    fun updateHunterPosition(pos: LatLng) {
        hunterPosition = pos
        updateCompassBearing()
    }

    fun updateRunnerPositions(positions: Map<String, LatLng>) {
        runnerPositions = positions
        updateCompassBearing()
        checkTrapTrigger()
    }

    private fun useReveal() {
        if (_revealActive.value == true || _revealOnCooldown.value == true) return

        _revealActive.value = true
        _revealRemainingMillis.value = 15_000L

        revealJob?.cancel()
        revealJob = viewModelScope.launch {
            val duration = 15_000L
            val start = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - start
                val remaining = duration - elapsed
                if (remaining <= 0L) break
                _revealRemainingMillis.postValue(remaining)
                delay(250L)
            }

            _revealActive.postValue(false)
            _revealRemainingMillis.postValue(0L)

            _revealOnCooldown.postValue(true)
            delay(30_000L)
            _revealOnCooldown.postValue(false)
        }
    }

    private fun useScan() {
        if (_scanActive.value == true) return

        _scanActive.value = true
        _scanRangeMultiplier.value = 1.5f

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            delay(10_000L)
            _scanActive.value = false
            _scanRangeMultiplier.value = 1.0f
        }
    }

    private fun useCompass() {
        if (_compassActive.value == true) return

        _compassActive.value = true
        _compassRemainingMillis.value = 15_000L

        compassJob?.cancel()
        compassJob = viewModelScope.launch {
            val duration = 15_000L
            val start = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - start
                val remaining = duration - elapsed
                if (remaining <= 0L) break
                _compassRemainingMillis.postValue(remaining)
                updateCompassBearing()
                delay(250L)
            }
            _compassActive.postValue(false)
            _compassRemainingMillis.postValue(0L)
            _compassBearingDeg.postValue(null)
        }
    }

    private fun updateCompassBearing() {
        if (_compassActive.value != true) return

        val hunter = hunterPosition ?: return
        if (runnerPositions.isEmpty()) return

        var bestId: String? = null
        var bestDist = Double.MAX_VALUE
        var bestPos: LatLng? = null

        for ((id, pos) in runnerPositions) {
            val d = distanceMeters(hunter, pos)
            if (d < bestDist) {
                bestDist = d
                bestId = id
                bestPos = pos
            }
        }

        if (bestId != null && bestPos != null) {
            val bearing = bearingDegrees(hunter, bestPos)
            _compassBearingDeg.value = bearing
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

    fun setTrap(center: LatLng, radiusMeters: Double = 30.0) {
        _trapCenter.value = center
        _trapRadiusMeters.value = radiusMeters
        _trapTriggeredRunnerId.value = null
    }

    private fun checkTrapTrigger() {
        val center = _trapCenter.value ?: return
        val radius = _trapRadiusMeters.value ?: return

        for ((id, pos) in runnerPositions) {
            val d = distanceMeters(center, pos)
            if (d <= radius) {
                _trapTriggeredRunnerId.value = id
                return
            }
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

    private fun startLockOnMode() {
        _lockOnRunnerId.value = null
        _lockOnRemainingMillis.value = 0L
    }

    fun lockOnFirstVisibleRunner(candidateRunnerId: String) {
        if (_lockOnRunnerId.value != null) return

        _lockOnRunnerId.value = candidateRunnerId
        _lockOnRemainingMillis.value = 10_000L

        lockOnJob?.cancel()
        lockOnJob = viewModelScope.launch {
            val duration = 10_000L
            val start = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - start
                val remaining = duration - elapsed
                if (remaining <= 0L) break
                _lockOnRemainingMillis.postValue(remaining)
                delay(250L)
            }
            _lockOnRunnerId.postValue(null)
            _lockOnRemainingMillis.postValue(0L)
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

    private fun bearingDegrees(a: LatLng, b: LatLng): Float {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val brng = Math.toDegrees(atan2(y, x))
        return ((brng + 360.0) % 360.0).toFloat()
    }
}
