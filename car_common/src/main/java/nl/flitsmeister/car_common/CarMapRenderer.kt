package nl.flitsmeister.car_common

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Job
import nl.flitsmeister.car_common.extentions.appManager
import nl.flitsmeister.car_common.extentions.runOnMainThread
import org.maplibre.android.maps.MapView

class CarMapRenderer(
    private val carContext: CarContext,
    serviceLifecycle: Lifecycle
) : SurfaceCallback, DefaultLifecycleObserver, ICarMapRenderer {

    // The map container used to handle the map lifecycle
    private val mapContainer = CarMapContainer(carContext, serviceLifecycle)

    private val osmPaint = Paint().apply {
        color = carContext.getColor(R.color.osm_attribution)
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.DEFAULT
    }

    // The surface to draw the map container on
    private var surfaceContainer: SurfaceContainer? = null

    // Handler to post actions to the UI thread
    private val uiHandler = Handler(Looper.getMainLooper())

    // The last known stable area
    private var lastKnownStableArea = Rect()
    private var lastKnownVisibleArea = Rect()

    private var currentTouchPoint: PointF? = null
    private var resetTouchPointJob: Job? = null

    init {
        serviceLifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        try {
            carContext.appManager.setSurfaceCallback(this)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Could not set surface callback", e)
            return
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        Log.v(LOG_TAG, "CarMapRenderer.onDestroy")
        surfaceContainer = null
        uiHandler.removeCallbacksAndMessages(null)
        try {
            carContext.appManager.setSurfaceCallback(null)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Could not remove surface callback", e)
        }
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        Log.v(LOG_TAG, "CarMapRenderer.onSurfaceAvailable")
        this.surfaceContainer = surfaceContainer
        mapContainer.setSurfaceSize(surfaceContainer.width, surfaceContainer.height)
        mapContainer.mapViewInstance?.apply {
            addOnDidBecomeIdleListener { drawOnSurface() }
            addOnWillStartRenderingFrameListener {
                drawOnSurface()
            }
        }
        runOnMainThread {
            // Start drawing the map on the android auto surface
            drawOnSurface()
        }
    }

    private fun drawOnSurface() {
        val mapView = mapContainer.mapViewInstance ?: return
        val surface = surfaceContainer?.surface ?: return

        val canvas = surface.lockHardwareCanvas()
        drawMapOnCanvas(mapView, canvas)
        surface.unlockCanvasAndPost(canvas)
    }

    private fun drawMapOnCanvas(mapView: MapView, canvas: Canvas) {
        val mapViewTextureView = mapView.takeIf { it.childCount > 0 }?.getChildAt(0) as? TextureView

        mapViewTextureView?.bitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
        }

        val density = carContext.resources.displayMetrics.density

        canvas.drawText(
            carContext.getString(R.string.copyright_openstreetmap),
            canvas.width - (12 * density),
            canvas.height - (4 * density),
            osmPaint.apply {
                textSize = 12 * density
            }
        )
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        if (visibleArea != lastKnownVisibleArea) {
            Log.v(
                LOG_TAG,
                "onVisibleAreaChanged left(${visibleArea.left}) top(${visibleArea.top}) right(${visibleArea.right}) bottom(${visibleArea.bottom})"
            )
            lastKnownVisibleArea = visibleArea
        }
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        if (stableArea != lastKnownStableArea) {
            Log.v(
                LOG_TAG,
                "onStableAreaChanged left(${stableArea.left}) top(${stableArea.top}) right(${stableArea.right}) bottom(${stableArea.bottom})"
            )
            //if only the vertical space has changed, you can ignore this mostly.
            lastKnownStableArea = stableArea
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        Log.v(LOG_TAG, "Surface destroyed")
        this.surfaceContainer = null
        uiHandler.removeCallbacksAndMessages(null)
    }

    override fun zoomInFromButton() {
        onScale(-1f, -1f, -1f)
    }

    override fun zoomOutFromButton() {
        onScale(-1f, -1f, 1f)
    }

    //Map interactivity
    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        Log.v(LOG_TAG, "onScale focusX($focusX) focusY($focusY) scaleFactor($scaleFactor)")
        val mapInstance = mapContainer.mapViewInstance ?: return
        //treat -1 as center
        val zoomX = if (focusX == -1f) {
            mapInstance.measuredWidth / 2f
        } else {
            focusX
        }
        val zoomY = if (focusY == -1f) {
            mapInstance.measuredHeight / 2f
        } else {
            focusY
        }
        val a = 100
        val b = 100
        //to try and keep it simple, just move on only x-direction
        generateZoomGesture(
            System.currentTimeMillis(),
            true,
            startPoint1 = PointF(zoomX - a - (b * scaleFactor), zoomY),
            startPoint2 = PointF(zoomX + a + (b * scaleFactor), zoomY),
            endPoint1 = PointF(zoomX - a, zoomY),
            endPoint2 = PointF(zoomX + a, zoomY),
            250,
            mapInstance
        )
    }

    @Synchronized
    override fun onScroll(distanceX: Float, distanceY: Float) {
        Log.v(LOG_TAG, "onScroll distanceX($distanceX) distanceY($distanceY)")
        mapContainer.scrollBy(distanceX, distanceY)

    }

    override fun onClick(x: Float, y: Float) {
        Log.v(LOG_TAG, "onClick x($x) y($y)")
        super.onClick(x, y)
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        Log.v(LOG_TAG, "onFling velocityX($velocityX) velocityY($velocityY)")
        super.onFling(velocityX, velocityY)
        // We don't need to implement onFling since the MapView does this for us
    }

    fun generateZoomGesture(
        startTime: Long, ifMove: Boolean, startPoint1: PointF,
        startPoint2: PointF, endPoint1: PointF,
        endPoint2: PointF, duration: Int, view: View
    ) {
        //https://stackoverflow.com/a/11599282/1398449
        val EVENT_MIN_INTERVAL = 10
        var eventTime = startTime
        var event: MotionEvent?
        var eventX1: Float = startPoint1.x
        var eventY1: Float = startPoint1.y
        var eventX2: Float = startPoint2.x
        var eventY2: Float = startPoint2.y

        // specify the property for the two touch points
        val properties: Array<MotionEvent.PointerProperties?> =
            arrayOfNulls<MotionEvent.PointerProperties>(2)
        val pp1 = MotionEvent.PointerProperties()
        pp1.id = 0
        pp1.toolType = MotionEvent.TOOL_TYPE_FINGER
        val pp2 = MotionEvent.PointerProperties()
        pp2.id = 1
        pp2.toolType = MotionEvent.TOOL_TYPE_FINGER
        properties[0] = pp1
        properties[1] = pp2

        //specify the coordinations of the two touch points
        //NOTE: you MUST set the pressure and size value, or it doesn't work
        val pointerCoords: Array<MotionEvent.PointerCoords?> =
            arrayOfNulls<MotionEvent.PointerCoords>(2)
        val pc1 = MotionEvent.PointerCoords()
        pc1.x = eventX1
        pc1.y = eventY1
        pc1.pressure = 1f
        pc1.size = 1f
        val pc2 = MotionEvent.PointerCoords()
        pc2.x = eventX2
        pc2.y = eventY2
        pc2.pressure = 1f
        pc2.size = 1f
        pointerCoords[0] = pc1
        pointerCoords[1] = pc2

        //////////////////////////////////////////////////////////////
        // events sequence of zoom gesture
        // 1. send ACTION_DOWN event of one start point
        // 2. send ACTION_POINTER_2_DOWN of two start points
        // 3. send ACTION_MOVE of two middle points
        // 4. repeat step 3 with updated middle points (x,y),
        //      until reach the end points
        // 5. send ACTION_POINTER_2_UP of two end points
        // 6. send ACTION_UP of one end point
        //////////////////////////////////////////////////////////////

        // step 1
        event = MotionEvent.obtain(
            startTime, eventTime,
            MotionEvent.ACTION_DOWN, 1, properties,
            pointerCoords, 0, 0, 1f, 1f, 0, 0, 0, 0
        )
        view.onTouchEvent(event)

        //step 2
        event = MotionEvent.obtain(
            startTime, eventTime,
            MotionEvent.ACTION_POINTER_2_DOWN, 2,
            properties, pointerCoords, 0, 0, 1f, 1f, 0, 0, 0, 0
        )
        view.onTouchEvent(event)

        //step 3, 4
        if (ifMove) {
            var moveEventNumber = 1
            moveEventNumber = duration / EVENT_MIN_INTERVAL
            val stepX1: Float = (endPoint1.x - startPoint1.x) / moveEventNumber
            val stepY1: Float = (endPoint1.y - startPoint1.y) / moveEventNumber
            val stepX2: Float = (endPoint2.x - startPoint2.x) / moveEventNumber
            val stepY2: Float = (endPoint2.y - startPoint2.y) / moveEventNumber
            for (i in 0 until moveEventNumber) {
                // update the move events
                eventTime += EVENT_MIN_INTERVAL
                eventX1 += stepX1
                eventY1 += stepY1
                eventX2 += stepX2
                eventY2 += stepY2
                pc1.x = eventX1
                pc1.y = eventY1
                pc2.x = eventX2
                pc2.y = eventY2
                pointerCoords[0] = pc1
                pointerCoords[1] = pc2
                event = MotionEvent.obtain(
                    startTime, eventTime,
                    MotionEvent.ACTION_MOVE, 2, properties,
                    pointerCoords, 0, 0, 1f, 1f, 0, 0, 0, 0
                )
                view.onTouchEvent(event)
            }
        }

        //step 5
        pc1.x = endPoint1.x
        pc1.y = endPoint1.y
        pc2.x = endPoint2.x
        pc2.y = endPoint2.y
        pointerCoords[0] = pc1
        pointerCoords[1] = pc2
        eventTime += EVENT_MIN_INTERVAL
        event = MotionEvent.obtain(
            startTime, eventTime,
            MotionEvent.ACTION_POINTER_2_UP, 2, properties,
            pointerCoords, 0, 0, 1f, 1f, 0, 0, 0, 0
        )
        view.onTouchEvent(event)

        // step 6
        eventTime += EVENT_MIN_INTERVAL
        event = MotionEvent.obtain(
            startTime, eventTime,
            MotionEvent.ACTION_UP, 1, properties,
            pointerCoords, 0, 0, 1f, 1f, 0, 0, 0, 0
        )
        view.onTouchEvent(event)
    }

    companion object {
        const val LOG_TAG = "CarMapRenderer"
    }
}