package nl.flitsmeister.maplibrecar

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.MapboxMapOptions
import com.mapbox.mapboxsdk.maps.Style
import nl.flitsmeister.car_common.R
import nl.flitsmeister.car_common.ResourceUtils
import nl.flitsmeister.maplibrecar.ui.theme.MapLibreCarTheme

class MainActivity : ComponentActivity() {

    var mapView: MapView? = null
    var mapBoxMap: MapboxMap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MapLibreCarTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AndroidView(modifier = Modifier.padding(innerPadding), factory = { context ->
                        //TextView(context).apply { setText("Hello MapLibreCar") }
                        val mapboxOptions = MapboxMapOptions.createFromAttributes(context).apply {
                            textureMode(true)
                            camera(
                                CameraPosition.Builder()
                                    .zoom(2.0)
                                    .target(LatLng(48.507879, 8.363795))
                                    .build()
                            )
                        }
                        Mapbox.getInstance(context)
                        mapView = MapView(context, mapboxOptions)
                        mapView?.onCreate(savedInstanceState)
                        mapView?.getMapAsync {
                            mapBoxMap = it
                            initMap(it)
                        }
                        mapView!!
                    })
                }
            }
        }
    }

    private fun initMap(map: MapboxMap) {
        try {
            map.setStyle(
                //TODO: Set your own style here!
                Style.Builder().fromJson(ResourceUtils.readRawResource(this, R.raw.local_style))
            )
        } catch (e: Exception) {
            Log.e("MapLibreCar", "Error setting local style", e)
        }
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }
}