package nl.flitsmeister.car_common

import android.graphics.Paint
import android.graphics.PixelFormat
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.car.app.CarContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import nl.flitsmeister.car_common.extentions.runOnMainThread
import nl.flitsmeister.car_common.extentions.windowManager
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

class CarMapContainer(
    private val carContext: CarContext, lifecycle: Lifecycle
) : DefaultLifecycleObserver {

    init {
        lifecycle.addObserver(this)
    }

    var mapViewInstance: MapView? = null
        private set

    var mapLibreMapInstance: MapLibreMap? = null

    var surfaceWidth: Int? = null
    var surfaceHeight: Int? = null

    fun scrollBy(x: Float, y: Float) {
        mapLibreMapInstance?.scrollBy(-x, -y, 0  )
    }
    
    /**
     * This function is called when the surface is created, to update the mapview with the surface sizes
     */
    fun setSurfaceSize(surfaceWidth: Int, surfaceHeight: Int) {
        Log.v(LOG_TAG, "setSurfaceSize: $surfaceWidth, $surfaceHeight")
        if (this.surfaceWidth != surfaceWidth || this.surfaceHeight != surfaceHeight) {
            this.surfaceWidth = surfaceWidth
            this.surfaceHeight = surfaceHeight
            mapViewInstance?.apply {
                carContext.windowManager.updateViewLayout(this, getWindowManagerLayoutParams())
            }
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        MapLibre.getInstance(carContext)

        runOnMainThread {
            mapViewInstance = createMapViewInstance().apply {
                // Add the mapView to a window using the windowManager. This is needed for the mapView to start rendering.
                // The mapView is not actually shown on any screen, but acts as though it is visible.
                carContext.windowManager.addView(
                    this,
                    getWindowManagerLayoutParams()
                )
                onStart()
                getMapAsync {
                    mapViewInstance = this
                    mapLibreMapInstance = it
                    it.setStyle(
                        //TODO: Set your own style here
                        Style.Builder().fromJson(ResourceUtils.readRawResource(carContext, R.raw.local_style))
                    )
                }
            }
        }
    }

    private fun getWindowManagerLayoutParams() = WindowManager.LayoutParams(
        surfaceWidth ?: WindowManager.LayoutParams.MATCH_PARENT,
        surfaceHeight ?: WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION,
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.RGBX_8888
    )

    override fun onDestroy(owner: LifecycleOwner) {
        runOnMainThread {
            mapLibreMapInstance = null

            mapViewInstance?.run {
                onStop()
                onDestroy()
                carContext.windowManager.removeView(this)
            }
            mapViewInstance = null
        }
    }

    private fun createMapViewInstance() =
        MapView(carContext, MapLibreMapOptions.createFromAttributes(carContext).apply {
            // Set the textureMode to true, so a TextureView is created
            // We can extract this TextureView to draw on the Android Auto surface
            textureMode(true)

        }).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, Paint())
        }

    companion object {
        const val LOG_TAG = "CarMapContainer"
    }
}