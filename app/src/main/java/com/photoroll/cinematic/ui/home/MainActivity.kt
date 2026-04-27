package com.photoroll.cinematic.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.photoroll.cinematic.R
import com.photoroll.cinematic.adapter.PhotoGridAdapter
import com.photoroll.cinematic.adapter.TimelineAdapter
import com.photoroll.cinematic.databinding.ActivityMainBinding
import com.photoroll.cinematic.model.CinematicStyle
import com.photoroll.cinematic.model.ScrollDirection
import com.photoroll.cinematic.ui.preview.PreviewActivity
import com.photoroll.cinematic.utils.MediaStoreHelper
import com.photoroll.cinematic.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var photoGridAdapter: PhotoGridAdapter
    private lateinit var timelineAdapter: TimelineAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadGalleryPhotos()
        else Toast.makeText(this, "Permission needed to access photos", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPhotoGrid()
        setupTimeline()
        setupScrollDirectionButtons()
        setupCinematicStyleSpinner()
        setupGenerateButton()
        observeViewModel()
        checkPermissionAndLoad()
    }

    private fun checkPermissionAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED ->
                loadGalleryPhotos()
            else -> permissionLauncher.launch(permission)
        }
    }

    private fun loadGalleryPhotos() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val photos = MediaStoreHelper.loadGalleryImages(this@MainActivity)
            viewModel.setGalleryPhotos(photos)
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun setupPhotoGrid() {
        photoGridAdapter = PhotoGridAdapter { photo ->
            viewModel.togglePhotoSelection(photo)
        }
        binding.recyclerPhotos.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = photoGridAdapter
        }
    }

    private fun setupTimeline() {
        timelineAdapter = TimelineAdapter { photo ->
            viewModel.removeFromTimeline(photo)
        }
        binding.recyclerTimeline.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = timelineAdapter
        }
    }

    private fun setupScrollDirectionButtons() {
        binding.btnScrollLeft.setOnClickListener {
            viewModel.setScrollDirection(ScrollDirection.LEFT)
            updateDirectionButtonStates(ScrollDirection.LEFT)
        }
        binding.btnScrollRight.setOnClickListener {
            viewModel.setScrollDirection(ScrollDirection.RIGHT)
            updateDirectionButtonStates(ScrollDirection.RIGHT)
        }
        binding.btnScrollUp.setOnClickListener {
            viewModel.setScrollDirection(ScrollDirection.UP)
            updateDirectionButtonStates(ScrollDirection.UP)
        }
        binding.btnScrollDown.setOnClickListener {
            viewModel.setScrollDirection(ScrollDirection.DOWN)
            updateDirectionButtonStates(ScrollDirection.DOWN)
        }
        updateDirectionButtonStates(ScrollDirection.RIGHT)
    }

    private fun updateDirectionButtonStates(selected: ScrollDirection) {
        val activeColor = ContextCompat.getColor(this, R.color.primary_blue)
        val inactiveColor = ContextCompat.getColor(this, R.color.surface_gray)

        listOf(
            binding.btnScrollLeft to ScrollDirection.LEFT,
            binding.btnScrollRight to ScrollDirection.RIGHT,
            binding.btnScrollUp to ScrollDirection.UP,
            binding.btnScrollDown to ScrollDirection.DOWN
        ).forEach { (btn, dir) ->
            btn.setCardBackgroundColor(if (dir == selected) activeColor else inactiveColor)
            val iconColor = if (dir == selected)
                ContextCompat.getColor(this, android.R.color.white)
            else
                ContextCompat.getColor(this, R.color.dark_gray)
            when (dir) {
                ScrollDirection.LEFT -> binding.icScrollLeft.setColorFilter(iconColor)
                ScrollDirection.RIGHT -> binding.icScrollRight.setColorFilter(iconColor)
                ScrollDirection.UP -> binding.icScrollUp.setColorFilter(iconColor)
                ScrollDirection.DOWN -> binding.icScrollDown.setColorFilter(iconColor)
            }
        }
    }

    private fun setupCinematicStyleSpinner() {
        val styles = CinematicStyle.values().map { it.displayName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, styles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCinematicStyle.adapter = adapter
        binding.spinnerCinematicStyle.setSelection(CinematicStyle.KEN_BURNS.ordinal)

        binding.spinnerCinematicStyle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.setCinematicStyle(CinematicStyle.values()[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupGenerateButton() {
        binding.btnGenerate.setOnClickListener {
            val selectedCount = viewModel.selectedPhotos.value?.size ?: 0
            if (selectedCount < 2) {
                Toast.makeText(this, "Please select at least 2 photos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.generateVideo()
        }
    }

    private fun observeViewModel() {
        viewModel.allPhotos.observe(this) { photos ->
            photoGridAdapter.submitList(photos)
            binding.tvNoPhotos.visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.selectedPhotos.observe(this) { selected ->
            timelineAdapter.submitList(selected)
            binding.tvTimelineEmpty.visibility = if (selected.isEmpty()) View.VISIBLE else View.GONE
            binding.tvSelectedCount.text = "${selected.size} photo${if (selected.size != 1) "s" else ""} selected"
        }

        viewModel.generateVideoEvent.observe(this) { config ->
            config?.let {
                val intent = Intent(this, PreviewActivity::class.java)
                intent.putExtra(PreviewActivity.EXTRA_DIRECTION, it.scrollDirection.name)
                intent.putExtra(PreviewActivity.EXTRA_STYLE, it.cinematicStyle.name)
                val uriList = ArrayList(it.photos.map { photo -> photo.uri.toString() })
                intent.putStringArrayListExtra(PreviewActivity.EXTRA_PHOTO_URIS, uriList)
                startActivity(intent)
                viewModel.onVideoGenerationHandled()
            }
        }
    }
}
