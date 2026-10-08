package com.ct.explorer.data.model

import androidx.compose.ui.graphics.Color

data class ColorTag(
    val id: String,
    val name: String,
    val colorHex: Long
) {
    val composeColor: Color get() = Color(colorHex)

    companion object {
        val PRESET_TAGS = listOf(
            ColorTag(id = "tag_red", name = "Important", colorHex = 0xFFFF4D4F),
            ColorTag(id = "tag_orange", name = "Work", colorHex = 0xFFFF7A45),
            ColorTag(id = "tag_yellow", name = "Review", colorHex = 0xFFFFC53D),
            ColorTag(id = "tag_green", name = "Personal", colorHex = 0xFF52C41A),
            ColorTag(id = "tag_blue", name = "Project", colorHex = 0xFF1890FF),
            ColorTag(id = "tag_purple", name = "Archive", colorHex = 0xFF9254DE)
        )

        fun findTag(id: String): ColorTag? = PRESET_TAGS.find { it.id == id }
    }
}
