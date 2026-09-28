package com.peyaj.jukeboxweb.disc

import com.fasterxml.jackson.annotation.JsonIgnore

data class CustomDisc(
    val id: String,
    val name: String,
    val author: String,
    val lore: List<String>,
    val durationSeconds: Int,
    val customModelData: Int = 0,
    val style: String = "cat"
) {
    @get:JsonIgnore
    val cleanId: String
        get() = cleanId(id)

    @get:JsonIgnore
    val effectiveCustomModelData: Int
        get() = if (customModelData != 0) customModelData else (10000 + Math.abs(cleanId.hashCode()) % 50000)

    fun formattedDuration(): String {
        val mins = durationSeconds / 60
        val secs = durationSeconds % 60
        return String.format("%d:%02d", mins, secs)
    }

    companion object {
        fun cleanId(rawId: String): String = rawId.lowercase().replace(Regex("[^a-z0-9_]"), "_")
    }
}

