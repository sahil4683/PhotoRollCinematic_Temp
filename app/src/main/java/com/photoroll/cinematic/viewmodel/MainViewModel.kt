package com.photoroll.cinematic.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.photoroll.cinematic.model.CinematicStyle
import com.photoroll.cinematic.model.PhotoItem
import com.photoroll.cinematic.model.ScrollDirection
import com.photoroll.cinematic.model.VideoConfig

class MainViewModel : ViewModel() {

    private val _allPhotos = MutableLiveData<List<PhotoItem>>(emptyList())
    val allPhotos: LiveData<List<PhotoItem>> = _allPhotos

    private val _selectedPhotos = MutableLiveData<List<PhotoItem>>(emptyList())
    val selectedPhotos: LiveData<List<PhotoItem>> = _selectedPhotos

    private val _scrollDirection = MutableLiveData(ScrollDirection.RIGHT)
    val scrollDirection: LiveData<ScrollDirection> = _scrollDirection

    private val _cinematicStyle = MutableLiveData(CinematicStyle.KEN_BURNS)
    val cinematicStyle: LiveData<CinematicStyle> = _cinematicStyle

    private val _generateVideoEvent = MutableLiveData<VideoConfig?>()
    val generateVideoEvent: LiveData<VideoConfig?> = _generateVideoEvent

    fun setGalleryPhotos(photos: List<PhotoItem>) {
        _allPhotos.value = photos
    }

    fun togglePhotoSelection(photo: PhotoItem) {
        val current = _allPhotos.value?.toMutableList() ?: return
        val idx = current.indexOfFirst { it.id == photo.id }
        if (idx == -1) return

        val selected = _selectedPhotos.value?.toMutableList() ?: mutableListOf()

        if (current[idx].isSelected) {
            current[idx] = current[idx].copy(isSelected = false, orderIndex = -1)
            selected.removeAll { it.id == photo.id }
            // Re-index remaining
            selected.forEachIndexed { i, p ->
                val gi = current.indexOfFirst { it.id == p.id }
                if (gi != -1) current[gi] = current[gi].copy(orderIndex = i)
            }
        } else {
            val orderIdx = selected.size
            current[idx] = current[idx].copy(isSelected = true, orderIndex = orderIdx)
            selected.add(current[idx])
        }

        _allPhotos.value = current
        _selectedPhotos.value = selected
    }

    fun removeFromTimeline(photo: PhotoItem) {
        val current = _allPhotos.value?.toMutableList() ?: return
        val selected = _selectedPhotos.value?.toMutableList() ?: return

        val gi = current.indexOfFirst { it.id == photo.id }
        if (gi != -1) current[gi] = current[gi].copy(isSelected = false, orderIndex = -1)

        selected.removeAll { it.id == photo.id }
        selected.forEachIndexed { i, p ->
            val idx = current.indexOfFirst { it.id == p.id }
            if (idx != -1) current[idx] = current[idx].copy(orderIndex = i)
        }

        _allPhotos.value = current
        _selectedPhotos.value = selected
    }

    fun setScrollDirection(direction: ScrollDirection) {
        _scrollDirection.value = direction
    }

    fun setCinematicStyle(style: CinematicStyle) {
        _cinematicStyle.value = style
    }

    fun generateVideo() {
        val photos = _selectedPhotos.value ?: emptyList()
        if (photos.isEmpty()) return

        val config = VideoConfig(
            photos = photos,
            scrollDirection = _scrollDirection.value ?: ScrollDirection.RIGHT,
            cinematicStyle = _cinematicStyle.value ?: CinematicStyle.KEN_BURNS
        )
        _generateVideoEvent.value = config
    }

    fun onVideoGenerationHandled() {
        _generateVideoEvent.value = null
    }

    fun clearAll() {
        _allPhotos.value = emptyList()
        _selectedPhotos.value = emptyList()
    }
}
