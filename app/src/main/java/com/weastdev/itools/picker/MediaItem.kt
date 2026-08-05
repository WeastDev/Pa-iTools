package com.weastdev.itools.picker

import android.net.Uri

/**
 * Representasi satu foto/video yang diambil dari MediaStore
 * (mencakup internal storage maupun SD card, karena MediaStore
 * otomatis mengindeks semua volume penyimpanan yang terpasang).
 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val durationMs: Long = 0L,
    val dateAdded: Long = 0L
) {
    /** Durasi video dalam format mm:ss, contoh "00:15". Kosong untuk foto. */
    fun formattedDuration(): String {
        if (!isVideo) return ""
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}

/** Filter yang aktif di bagian atas layar (tombol All / Video / Photo). */
enum class MediaFilter {
    ALL, VIDEO, PHOTO
}
