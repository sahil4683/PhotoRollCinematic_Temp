package com.photoroll.cinematic.ui.preview

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.photoroll.cinematic.databinding.ActivityPreviewBinding
import com.photoroll.cinematic.model.CinematicStyle
import com.photoroll.cinematic.model.PhotoItem
import com.photoroll.cinematic.model.ScrollDirection
import com.photoroll.cinematic.utils.VideoEncoder
import kotlinx.coroutines.launch

class PreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DIRECTION  = "extra_direction"
        const val EXTRA_STYLE      = "extra_style"
        const val EXTRA_PHOTO_URIS = "extra_photo_uris"
    }

    private lateinit var binding: ActivityPreviewBinding
    private var savedVideoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val directionName   = intent.getStringExtra(EXTRA_DIRECTION)  ?: ScrollDirection.RIGHT.name
        val styleName       = intent.getStringExtra(EXTRA_STYLE)      ?: CinematicStyle.KEN_BURNS.name
        val photoUriStrings = intent.getStringArrayListExtra(EXTRA_PHOTO_URIS) ?: arrayListOf()

        val direction = ScrollDirection.valueOf(directionName)
        val style     = CinematicStyle.valueOf(styleName)

        binding.tvStyleInfo.text     = "Style: ${style.displayName}"
        binding.tvDirectionInfo.text = "Direction: ${direction.name.lowercase().replaceFirstChar { it.uppercase() }}"
        binding.tvPhotoCount.text    = "${photoUriStrings.size} photo${if (photoUriStrings.size != 1) "s" else ""}"

        val photos = photoUriStrings.mapIndexed { i, uriStr ->
            PhotoItem(id = i.toLong(), uri = Uri.parse(uriStr), displayName = "Photo $i")
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnExport.setOnClickListener {
            savedVideoUri?.let { shareOrOpenVideo(it) }
                ?: Toast.makeText(this, "Video not ready yet", Toast.LENGTH_SHORT).show()
        }

        startEncoding(photos, direction, style)
    }

    private fun startEncoding(
        photos: List<PhotoItem>,
        direction: ScrollDirection,
        style: CinematicStyle
    ) {
        binding.tvPreviewTitle.text = "Generating Video..."
        binding.progressGeneration.visibility = View.VISIBLE
        binding.progressGeneration.progress = 0
        binding.btnExport.visibility = View.GONE
        binding.tvStatus.text = "Starting..."

        lifecycleScope.launch {
            try {
                val uri = VideoEncoder.encode(
                    context         = this@PreviewActivity,
                    photos          = photos,
                    scrollDirection = direction,
                    cinematicStyle  = style,
                    onProgress      = { progress ->
                        runOnUiThread {
                            binding.progressGeneration.progress = progress.percent
                            binding.tvStatus.text = progress.message
                        }
                    }
                )

                savedVideoUri = uri

                runOnUiThread {
                    binding.tvPreviewTitle.text  = "Video Ready! 🎬"
                    binding.tvStatus.text        = "Saved to Movies/PhotoRollCinematic"
                    binding.btnExport.text       = "Share / Open Video"
                    binding.btnExport.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    binding.tvPreviewTitle.text = "Encoding Failed"
                    binding.tvStatus.text = e.message ?: "Unknown error"
                    Toast.makeText(this@PreviewActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun shareOrOpenVideo(uri: Uri) {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(viewIntent, "Open or share video")
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(shareIntent))
        startActivity(chooser)
    }
}
