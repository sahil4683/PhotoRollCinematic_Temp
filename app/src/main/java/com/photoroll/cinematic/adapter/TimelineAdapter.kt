package com.photoroll.cinematic.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.photoroll.cinematic.R
import com.photoroll.cinematic.model.PhotoItem

class TimelineAdapter(
    private val onRemoveClick: (PhotoItem) -> Unit
) : ListAdapter<PhotoItem, TimelineAdapter.TimelineViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimelineViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timeline, parent, false)
        return TimelineViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimelineViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TimelineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgPhoto: ImageView = itemView.findViewById(R.id.img_timeline_photo)
        private val btnRemove: ImageView = itemView.findViewById(R.id.btn_remove)

        fun bind(item: PhotoItem) {
            Glide.with(itemView.context)
                .load(item.uri)
                .transform(CenterCrop())
                .placeholder(R.drawable.placeholder_photo)
                .into(imgPhoto)

            btnRemove.setOnClickListener { onRemoveClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PhotoItem>() {
        override fun areItemsTheSame(old: PhotoItem, new: PhotoItem) = old.id == new.id
        override fun areContentsTheSame(old: PhotoItem, new: PhotoItem) = old == new
    }
}
