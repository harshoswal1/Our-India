package com.ourindia.app.data.geo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import java.util.zip.GZIPInputStream
import timber.log.Timber

/** Resolved geographic location from GPS coordinates */
data class ResolvedLocation(
    val country: String = "India",
    val state: String?,
    val district: String? = null,
    val city: String? = null,
    val taluka: String? = null
)

/** Abstraction for resolving geographical coordinates to structured location info */
interface GeographicResolver {
    suspend fun resolveLocation(latitude: Double, longitude: Double): ResolvedLocation?
    // Backward compat
    suspend fun resolveRegion(latitude: Double, longitude: Double): String? {
        return resolveLocation(latitude, longitude)?.state
    }
}

/**
 * Local region resolution using bundled optimized GeoJSON files (gzipped).
 * Implements precise Point-in-Polygon (PiP) algorithms using MapLibre Turf.
 */
class LocalGeographicResolver(private val context: Context) : GeographicResolver {

    // Lazy initialization saves memory by only loading boundary files into memory
    // exactly when they are required for resolution.
    private val stateFeatures by lazy { loadFeatures("geo/states.geojson.gz") }
    private val districtFeatures by lazy { loadFeatures("geo/districts.geojson.gz") }
    private val subDistrictFeatures by lazy { loadFeatures("geo/sub_districts.geojson.gz") }

    private fun loadFeatures(assetPath: String): FeatureCollection? {
        return try {
            val jsonString = context.assets.open(assetPath).use { input ->
                GZIPInputStream(input).bufferedReader().use { it.readText() }
            }
            FeatureCollection.fromJson(jsonString)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load/decompress bundled GeoJSON: $assetPath")
            null
        }
    }

    override suspend fun resolveLocation(latitude: Double, longitude: Double): ResolvedLocation? = withContext(Dispatchers.Default) {
        val point = Point.fromLngLat(longitude, latitude)
        
        var stateName: String? = null
        var districtName: String? = null
        var subDistrictName: String? = null

        // 1. Resolve State (Top level)
        stateFeatures?.features()?.firstOrNull { feature ->
            val geom = feature.geometry()
            if (geom is Polygon) isPointInPolygon(point, geom)
            else if (geom is MultiPolygon) isPointInMultiPolygon(point, geom)
            else false
        }?.let { feature ->
            stateName = feature.getStringProperty("state")
        }

        if (stateName == null) {
            // Not falling within any Indian state boundaries
            return@withContext null
        }

        // 2. Resolve District
        districtFeatures?.features()?.firstOrNull { feature ->
            // Optimization: Only check PiP for districts inside the resolved state
            if (feature.getStringProperty("state")?.equals(stateName, ignoreCase = true) == true) {
                val geom = feature.geometry()
                if (geom is Polygon) isPointInPolygon(point, geom)
                else if (geom is MultiPolygon) isPointInMultiPolygon(point, geom)
                else false
            } else false
        }?.let { feature ->
            districtName = feature.getStringProperty("district")
        }

        // 3. Resolve Sub-district
        if (districtName != null) {
            subDistrictFeatures?.features()?.firstOrNull { feature ->
                // Optimization: Only check PiP for sub-districts inside the resolved district
                val matchesState = feature.getStringProperty("state")?.equals(stateName, ignoreCase = true) == true
                val matchesDistrict = feature.getStringProperty("district")?.equals(districtName, ignoreCase = true) == true
                
                if (matchesState && matchesDistrict) {
                    val geom = feature.geometry()
                    if (geom is Polygon) isPointInPolygon(point, geom)
                    else if (geom is MultiPolygon) isPointInMultiPolygon(point, geom)
                    else false
                } else false
            }?.let { feature ->
                subDistrictName = feature.getStringProperty("subdistrict")
            }
        }

        ResolvedLocation(
            country = "India",
            state = stateName,
            district = districtName,
            taluka = subDistrictName
        )
    }

    // ── Point-In-Polygon (Ray-Casting Algorithm) ──────────────────────────

    private fun isPointInMultiPolygon(point: Point, multiPolygon: MultiPolygon): Boolean {
        for (polygonCoords in multiPolygon.coordinates()) {
            if (isPointInPolygonCoords(point, polygonCoords)) {
                return true
            }
        }
        return false
    }

    private fun isPointInPolygon(point: Point, polygon: Polygon): Boolean {
        return isPointInPolygonCoords(point, polygon.coordinates())
    }

    private fun isPointInPolygonCoords(point: Point, coords: List<List<Point>>): Boolean {
        if (coords.isEmpty()) return false
        
        // The first list is the outer boundary, subsequent lists are holes.
        val outerRing = coords[0]
        var isInside = rayCast(point, outerRing)
        
        // If inside outer ring, check if it's inside any hole (if it is, it's outside the polygon)
        if (isInside) {
            for (i in 1 until coords.size) {
                if (rayCast(point, coords[i])) {
                    return false
                }
            }
        }
        return isInside
    }

    private fun rayCast(point: Point, ring: List<Point>): Boolean {
        var isInside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val pi = ring[i]
            val pj = ring[j]
            
            val intersects = ((pi.latitude() > point.latitude()) != (pj.latitude() > point.latitude())) &&
                (point.longitude() < (pj.longitude() - pi.longitude()) * (point.latitude() - pi.latitude()) / (pj.latitude() - pi.latitude()) + pi.longitude())
            
            if (intersects) {
                isInside = !isInside
            }
            j = i
        }
        return isInside
    }
}
