package com.weastdev.itools.picker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.weastdev.itools.R

/**
 * Menampilkan grid foto/video. Menerima dua callback dari Activity:
 * - [getSelectionNumber] untuk tahu apakah sebuah item sedang terpilih & urutan nomornya
 * - [onItemClick] dipanggil saat item di-tap, untuk toggle status pilih
 */
class MediaAdapter(
    private val getSelectionNumber: (MediaItem) -> Int?,
    private val onItemClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<MediaAdapter.MediaViewHolder>() {

    private val items = mutableListOf<MediaItem>()

    fun submitList(newItems: List<MediaItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    /** Dipanggil saat status pilih berubah, supaya semua badge nomor ter-refresh. */
    fun refreshSelectionState() {
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media_grid, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class MediaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgThumb: ImageView = itemView.findViewById(R.id.imgThumb)
        private val txtDuration: TextView = itemView.findViewById(R.id.txtDuration)
        private val selectionOverlay: View = itemView.findViewById(R.id.selectionOverlay)
        private val selectionBorder: View = itemView.findViewById(R.id.selectionBorder)
        private val txtNumber: TextView = itemView.findViewById(R.id.txtNumber)

        fun bind(item: MediaItem) {
            Glide.with(itemView)
                .load(item.uri)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .centerCrop()
                .placeholder(R.color.thumb_placeholder)
                .into(imgThumb)

            if (item.isVideo) {
                txtDuration.visibility = View.VISIBLE
                txtDuration.text = item.formattedDuration()
            } else {
                txtDuration.visibility = View.GONE
            }

            val selectionNumber = getSelectionNumber(item)
            val isSelected = selectionNumber != null
            selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
            selectionBorder.visibility = if (isSelected) View.VISIBLE else View.GONE
            if (isSelected) {
                txtNumber.visibility = View.VISIBLE
                txtNumber.text = selectionNumber.toString()
            } else {
                txtNumber.visibility = View.GONE
            }

            itemView.setOnClickListener { onItemClick(item) }
        }
    }
}
