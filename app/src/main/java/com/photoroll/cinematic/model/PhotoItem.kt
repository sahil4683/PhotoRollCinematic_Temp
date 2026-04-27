package com.photoroll.cinematic.model

import android.net.Uri

data class PhotoItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    var isSelected: Boolean = false,
    var orderIndex: Int = -1
)

enum class ScrollDirection {
    LEFT, RIGHT, UP, DOWN
}

enum class CinematicStyle(val displayName: String) {
    SLOW_PAN("Slow Pan"),
    ZOOM_IN("Zoom In"),
    KEN_BURNS("Ken Burns"),
    CUSTOM_DAVINCI("Custom (DaVinci Resolve)")
}

data class VideoConfig(
    val photos: List<PhotoItem>,
    val scrollDirection: ScrollDirection,
    val cinematicStyle: CinematicStyle,
    val durationPerPhoto: Int = 3  // seconds
)
