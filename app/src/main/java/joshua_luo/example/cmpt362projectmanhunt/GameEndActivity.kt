package joshua_luo.example.cmpt362projectmanhunt

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import android.graphics.Color

class GameEndActivity : FragmentActivity(), OnMapReadyCallback {

    private lateinit var tvFinalTime: TextView
    private lateinit var tvRemainingSurvivor: TextView
    private lateinit var tvLongestDistance: TextView
    private lateinit var tvYouTravelled: TextView
    private lateinit var btnMainMenu: Button
    private var googleMap: GoogleMap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_end)

        tvFinalTime = findViewById(R.id.tvFinalTime)
        tvRemainingSurvivor = findViewById(R.id.tvRemainingSurvivor)
        tvLongestDistance = findViewById(R.id.tvLongestDistance)
        tvYouTravelled = findViewById(R.id.tvYouTravelled)
        btnMainMenu = findViewById(R.id.btnMainMenu)


        val finalTime = intent.getStringExtra("finalTime") ?: "0min 0sec"
        val isHunter = intent.getBooleanExtra("isHunter", false)

        tvFinalTime.text = finalTime

        // TODO: Get actual game statistics
        // For now, using placeholder data
        tvRemainingSurvivor.text = "N/A"
        tvLongestDistance.text = "0km"
        tvYouTravelled.text = "0km"

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        btnMainMenu.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // TODO: Display actual game boundary and player paths
        // For now, just show a basic map view


        val defaultLocation = LatLng(49.2827, -123.1207) // Vancouver
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f))
    }

    private fun drawPath(pathPoints: List<LatLng>) {
        if (pathPoints.isEmpty()) return


        val polylineOptions = PolylineOptions()
            .addAll(pathPoints)
            .color(Color.BLUE)
            .width(10f)

        googleMap?.addPolyline(polylineOptions)


        if (pathPoints.isNotEmpty()) {
            googleMap?.addMarker(
                MarkerOptions()
                    .position(pathPoints.first())
                    .title("Start")
            )

            googleMap?.addMarker(
                MarkerOptions()
                    .position(pathPoints.last())
                    .title("End")
            )
        }

        if (pathPoints.size > 1) {
            val boundsBuilder = LatLngBounds.Builder()
            pathPoints.forEach { boundsBuilder.include(it) }
            val bounds = boundsBuilder.build()
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        }
    }
}
