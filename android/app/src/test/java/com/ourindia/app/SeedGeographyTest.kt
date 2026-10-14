package com.ourindia.app

import com.ourindia.app.ui.partystructure.PartySeedData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * The map tints a polygon only when a seed's state/district matches the boundary file
 * exactly (case aside). A spelling drift ("Bengaluru" vs the file's "Bangalore") fails
 * silently on device, so it is checked here instead.
 */
class SeedGeographyTest {

    private fun features(name: String) = File("src/main/assets/geo/$name").inputStream().use { input ->
        Json.parseToJsonElement(GZIPInputStream(input).bufferedReader().readText())
            .jsonObject.getValue("features").jsonArray
            .map { it.jsonObject.getValue("properties").jsonObject }
    }

    @Test
    fun everySeedStateAndDistrictExistsOnTheMap() {
        val states = features("states.geojson.gz").map { it.getValue("state").jsonPrimitive.content.uppercase() }.toSet()
        val districts = features("districts.geojson.gz").map {
            it.getValue("state").jsonPrimitive.content.uppercase() to it.getValue("district").jsonPrimitive.content.uppercase()
        }.toSet()

        val problems = PartySeedData.parties.flatMap { PartySeedData.nodesFor(it) }.mapNotNull { n ->
            val st = n.state?.uppercase() ?: return@mapNotNull null
            when {
                st !in states -> "${n.id}: state '${n.state}' not in states file"
                n.district != null && (st to n.district!!.uppercase()) !in districts ->
                    "${n.id}: district '${n.district}' not in ${n.state}"
                else -> null
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
