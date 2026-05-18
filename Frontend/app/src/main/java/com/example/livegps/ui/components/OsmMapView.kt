package com.example.livegps.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.livegps.data.model.LocationSample
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Creates a lifecycle-aware osmdroid [MapView]. Hoisting it lets the dashboard's
 * own zoom / recenter / layer controls drive the map directly.
 */
@Composable
fun rememberMapView(): MapView {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(4.0)
            controller.setCenter(GeoPoint(20.5937, 78.9629))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }
    return mapView
}

/** Renders the hoisted [mapView] in Compose with a single live marker. */
@Composable
fun OsmMapView(
    mapView: MapView,
    location: LocationSample?,
    modifier: Modifier = Modifier,
    follow: Boolean = true,
) {
    val marker = remember(mapView) {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Current location"
        }
    }
    var centeredOnce by remember { mutableStateOf(false) }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { mv ->
            val loc = location ?: return@AndroidView
            val point = GeoPoint(loc.lat, loc.lng)
            marker.position = point
            if (!mv.overlays.contains(marker)) mv.overlays.add(marker)
            if (!centeredOnce) {
                mv.controller.setZoom(17.0)
                mv.controller.setCenter(point)
                centeredOnce = true
            } else if (follow) {
                mv.controller.animateTo(point)
            }
            mv.invalidate()
        },
    )
}
