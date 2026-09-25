package com.peyaj.jukeboxweb.disc

data class CustomDisc(
    val id: String,
    val name: String,
    val author: String,
    val lore: List<String>,
    val durationSeconds: Int,
    val customModelData: Int = 0,
    val style: String = "cat"
)
