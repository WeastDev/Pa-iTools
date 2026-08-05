package com.weastdev.itools.picker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.weastdev.itools.R

class SelectedMediaAdapter(
    private val onRemove: (MediaItem) -> Unit
) : RecyclerView.Adapter<SelectedMediaAdapter.SelectedViewHolder>() {

    private val items = mutableListOf<MediaItem>()

    fun submitList(newItems: List<MediaItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectedViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selected_media, parent, false)
        return SelectedViewHolder(view)
    }

    override fun onBindViewHolder(holder: SelectedViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class SelectedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgThumb: ImageView = itemView.findViewById(R.id.imgSelectedThumb)
        private val btnRemove: ImageButton = itemView.findViewById(R.id.btnRemove)

        fun bind(item: MediaItem) {
            Glide.with(itemView)
                .load(item.uri)
                .centerCrop()
                .into(imgThumb)

            btnRemove.setOnClickListener { onRemove(item) }
        }
    }
}
