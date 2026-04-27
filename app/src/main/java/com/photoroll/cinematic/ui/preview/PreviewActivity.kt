package com.photoroll.cinematic.ui.preview

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.photoroll.cinematic.databinding.ActivityPreviewBinding
import com.photoroll.cinematic.model.CinematicStyle
import com.photoroll.cinematic.model.ScrollDirection

class PreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DIRECTION = "extra_direction"
        const val EXTRA_STYLE = "extra_style"
        const val EXTRA_PHOTO_COUNT = "extra_photo_count"
    }

    private lateinit var binding: ActivityPreviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val directionName = intent.getStringExtra(EXTRA_DIRECTION) ?: ScrollDirection.RIGHT.name
        val styleName = intent.getStringExtra(EXTRA_STYLE) ?: CinematicStyle.KEN_BURNS.name
        val photoCount = intent.getIntExtra(EXTRA_PHOTO_COUNT, 0)

        val direction = ScrollDirection.valueOf(directionName)
        val style = CinematicStyle.valueOf(styleName)

        binding.tvPreviewTitle.text = "Generating Video..."
        binding.tvStyleInfo.text = "Style: ${style.displayName}"
        binding.tvDirectionInfo.text = "Direction: ${direction.name.lowercase().replaceFirstChar { it.uppercase() }}"
        binding.tvPhotoCount.text = "$photoCount photos"

        simulateGeneration()

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun simulateGeneration() {
        binding.progressGeneration.visibility = View.VISIBLE
        binding.tvStatus.text = "Processing photos..."

        binding.root.postDelayed({
            binding.progressGeneration.progress = 33
            binding.tvStatus.text = "Applying cinematic effects..."
        }, 1000)

        binding.root.postDelayed({
            binding.progressGeneration.progress = 66
            binding.tvStatus.text = "Rendering video..."
        }, 2000)

        binding.root.postDelayed({
            binding.progressGeneration.progress = 100
            binding.tvPreviewTitle.text = "Video Ready!"
            binding.tvStatus.text = "Your cinematic video has been generated"
            binding.btnExport.visibility = View.VISIBLE
        }, 3000)
    }
}
