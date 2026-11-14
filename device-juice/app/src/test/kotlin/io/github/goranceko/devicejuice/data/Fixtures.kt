package io.github.goranceko.devicejuice.data

import io.hammerhead.karooext.models.SavedDevices
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/** `SavedDevices` captured on the rider's Karoo 3 during the milestone-0 spike (see SPIKE.md). */
object Fixtures {
    data class Loaded(val capturedAt: Long, val savedDevices: SavedDevices)

    private val json = Json { ignoreUnknownKeys = true }

    fun load(name: String): Loaded {
        val text = checkNotNull(javaClass.classLoader?.getResource("fixtures/$name.json")) { "missing fixture $name" }.readText()
        val root = json.parseToJsonElement(text).jsonObject
        return Loaded(
            capturedAt = Instant.parse(root.getValue("capturedAt").jsonPrimitive.content).toEpochMilli(),
            savedDevices = json.decodeFromJsonElement(SavedDevices.serializer(), root.getValue("savedDevices")),
        )
    }
}
