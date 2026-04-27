package com.photoroll.cinematic.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.photoroll.cinematic.R
import com.photoroll.cinematic.model.PhotoItem

class PhotoGridAdapter(
    private val onPhotoClick: (PhotoItem) -> Unit
) : ListAdapter<PhotoItem, PhotoGridAdapter.PhotoViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo_grid, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgPhoto: ImageView = itemView.findViewById(R.id.img_photo)
        private val viewOverlay: View = itemView.findViewById(R.id.view_selected_overlay)
        private val imgCheck: ImageView = itemView.findViewById(R.id.img_check)
        private val tvOrder: TextView = itemView.findViewById(R.id.tv_order)

        fun bind(item: PhotoItem) {
            Glide.with(itemView.context)
                .load(item.uri)
                .transform(CenterCrop())
                .placeholder(R.drawable.placeholder_photo)
                .into(imgPhoto)

            viewOverlay.visibility = if (item.isSelected) View.VISIBLE else View.GONE
            imgCheck.visibility = if (item.isSelected) View.VISIBLE else View.GONE
            tvOrder.visibility = if (item.isSelected) View.VISIBLE else View.GONE
            if (item.isSelected) {
                tvOrder.text = (item.orderIndex + 1).toString()
            }

            itemView.setOnClickListener { onPhotoClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PhotoItem>() {
        override fun areItemsTheSame(old: PhotoItem, new: PhotoItem) = old.id == new.id
        override fun areContentsTheSame(old: PhotoItem, new: PhotoItem) = old == new
    }
}
