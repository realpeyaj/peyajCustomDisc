package com.peyaj.jukeboxweb.disc

data class CustomDisc(
    val id: String,
    val name: String,
    val author: String,
    val lore: List<String>,
    val durationSeconds: Int,
    val customModelData: Int = 0, // For visual customization if needed later
    val style: String = "cat" // Disc style: cat, blocks, chirp, far, mall, mellohi, stal, strad, ward, wait, pigstep, otherside, 5, relic, creator, precipice, 13, 11
)

