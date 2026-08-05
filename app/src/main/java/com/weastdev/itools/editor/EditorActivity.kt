package com.weastdev.itools.editor

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.Glide
import com.weastdev.itools.R
import com.weastdev.itools.databinding.ActivityEditorBinding
import com.weastdev.itools.picker.MediaPickerActivity

/**
 * Layar setelah user menekan tombol Import di MediaPickerActivity.
 *
 * Catatan: bottom bar (Trim / Audio / Text / Sticker / PIP / Effects / Filters)
 * sengaja belum ditambahkan di update ini - menyusul di update berikutnya.
 * Begitu juga tombol Cover, yang untuk saat ini masih placeholder.
 */
class EditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MEDIA_URIS = "extra_media_uris"

        /** Durasi default untuk foto di timeline (foto tidak punya durasi asli). */
        private const val DEFAULT_PHOTO_DURATION_MS = 3000L

        /** Lebar 1 detik di ruler & di clip strip (dibuat sama supaya playhead sinkron). */
        private const val SECOND_WIDTH_DP = 46

        private const val TICK_INTERVAL_MS = 200L
    }

    private lateinit var binding: ActivityEditorBinding

    private data class ClipItem(
        val uri: Uri,
        val isVideo: Boolean,
        val durationMs: Long
    )

    private val clips = mutableListOf<ClipItem>()
    private val clipStartOffsetsMs = mutableListOf<Long>()
    private var totalDurationMs = 0L

    private var currentClipIndex = 0
    /** Posisi di dalam klip yang sedang tampil (bukan posisi global). */
    private var positionInClipMs = 0L
    private var isPlaying = false
    private var isFullscreen = false
    private var isMuted = false
    private var isSyncingScroll = false
    /** true saat kode yang men-scroll (playback tick / seek), bukan jari user. */
    private var isProgrammaticScroll = false
    /** Video yang sedang di-load: MediaPlayer disimpan supaya volume bisa diubah tanpa
     *  menimpa OnPreparedListener (dulu applyVolume() menimpa listener ini, jadi kadang
     *  frame pertama video tidak sempat digambar -> preview kelihatan hitam/kosong). */
    private var currentMediaPlayer: android.media.MediaPlayer? = null
    /** Waktu (ms, relatif ke klip) yang harus di-seek begitu video baru selesai prepare,
     *  dipakai saat user scrub pindah ke klip lain. */
    private var pendingSeekMs: Long? = null
    /** Setengah lebar area ruler & clip-strip yang terlihat, dipakai sebagai padding kiri/kanan
     *  supaya playhead yang diam di tengah bisa align ke detik manapun, termasuk 00:00 dan akhir. */
    private var centerPaddingPx = 0

    private val tickHandler = Handler(Looper.getMainLooper())
    private var lastTickAt = 0L

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying) return
            val now = System.currentTimeMillis()
            val elapsed = if (lastTickAt == 0L) 0L else now - lastTickAt
            lastTickAt = now

            val clip = clips.getOrNull(currentClipIndex)
            if (clip != null && !clip.isVideo) {
                // Foto: kita yang mensimulasikan waktunya berjalan.
                positionInClipMs += elapsed
                if (positionInClipMs >= clip.durationMs) {
                    goToClip(currentClipIndex + 1, autoPlay = true)
                    tickHandler.postDelayed(this, TICK_INTERVAL_MS)
                    return
                }
            } else if (clip != null && clip.isVideo) {
                // Video: posisi diambil langsung dari VideoView supaya akurat.
                positionInClipMs = binding.videoPreview.currentPosition.toLong()
                if (!binding.videoPreview.isPlaying && positionInClipMs > 0) {
                    goToClip(currentClipIndex + 1, autoPlay = true)
                    tickHandler.postDelayed(this, TICK_INTERVAL_MS)
                    return
                }
            }

            updateTimelineUi()
            tickHandler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    /** Dipakai saat user menekan X: balik ke layar pilih foto/video, lalu terima hasil baru. */
    private val reopenPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uris: ArrayList<Uri>? = result.data
                    ?.getParcelableArrayListExtra(MediaPickerActivity.EXTRA_SELECTED_URIS)
                if (!uris.isNullOrEmpty()) {
                    loadClips(uris)
                } else {
                    finish()
                }
            } else {
                finish()
            }
        }

    /** Dipakai saat user menekan tombol + di clip strip untuk menambah klip lagi. */
    private val addClipLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uris: ArrayList<Uri>? = result.data
                    ?.getParcelableArrayListExtra(MediaPickerActivity.EXTRA_SELECTED_URIS)
                if (!uris.isNullOrEmpty()) {
                    loadClips(uris)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTopBar()
        setupTransportRow()
        setupVolumeControl()
        setupAddRows()
        setupScrollSync()

        val uris: ArrayList<Uri>? = intent.getParcelableArrayListExtra(EXTRA_MEDIA_URIS)
        if (uris.isNullOrEmpty()) {
            finish()
            return
        }
        loadClips(uris)
    }

    override fun onPause() {
        super.onPause()
        pausePlayback()
    }

    override fun onDestroy() {
        super.onDestroy()
        tickHandler.removeCallbacks(tickRunnable)
    }

    // ============================== Top bar ==============================

    private fun setupTopBar() {
        binding.btnClose.setOnClickListener {
            pausePlayback()
            reopenPickerLauncher.launch(Intent(this, MediaPickerActivity::class.java))
        }
        binding.btnExport.setOnClickListener {
            toast("Export - belum diimplementasi")
        }
        binding.btnMore.setOnClickListener {
            toast("Menu lainnya - belum diimplementasi")
        }
        binding.btnExitFullscreen.setOnClickListener { setFullscreen(false) }
    }

    // ============================== Transport row ==============================

    private fun setupTransportRow() {
        // Tombol "belok kiri" & "belok kanan" di sisi kiri baris transport.
        binding.btnUndo.setOnClickListener { toast("Undo - belum diimplementasi") }
        binding.btnRedo.setOnClickListener { toast("Redo - belum diimplementasi") }

        binding.btnPlayPause.setOnClickListener { togglePlayback() }

        binding.btnFullscreen.setOnClickListener { setFullscreen(true) }
    }

    private fun togglePlayback() {
        if (isPlaying) pausePlayback() else startPlayback()
    }

    private fun startPlayback() {
        if (clips.isEmpty()) return
        isPlaying = true
        lastTickAt = 0L
        binding.btnPlayPause.setImageResource(R.drawable.ic_pause)

        val clip = clips[currentClipIndex]
        if (clip.isVideo) {
            binding.videoPreview.start()
        }
        tickHandler.removeCallbacks(tickRunnable)
        tickHandler.post(tickRunnable)
    }

    private fun pausePlayback() {
        if (!isPlaying) return
        isPlaying = false
        binding.btnPlayPause.setImageResource(R.drawable.ic_play)
        if (clips.getOrNull(currentClipIndex)?.isVideo == true) {
            binding.videoPreview.pause()
        }
        tickHandler.removeCallbacks(tickRunnable)
    }

    // ============================== Volume ==============================

    private fun setupVolumeControl() {
        binding.btnVolume.setOnClickListener {
            if (binding.seekVolume.visibility == View.VISIBLE) {
                binding.seekVolume.visibility = View.GONE
            } else {
                binding.seekVolume.visibility = View.VISIBLE
            }
        }
        binding.btnVolume.setOnLongClickListener {
            isMuted = !isMuted
            applyVolume(if (isMuted) 0 else binding.seekVolume.progress)
            true
        }
        binding.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                isMuted = progress == 0
                applyVolume(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun applyVolume(progress: Int) {
        // PENTING: jangan panggil binding.videoPreview.setOnPreparedListener(...) di sini.
        // Dulu baris itu MENIMPA listener yang sedang dipakai showClip() untuk menampilkan
        // frame pertama video, sehingga preview video kadang gagal tampil.
        // Sekarang cukup atur volume langsung ke MediaPlayer yang sudah aktif (kalau ada).
        val level = progress / 100f
        currentMediaPlayer?.setVolume(level, level)
        binding.btnVolume.setImageResource(
            if (progress == 0) R.drawable.ic_volume_mute else R.drawable.ic_volume
        )
    }

    // ============================== Add music / add text ==============================

    private fun setupAddRows() {
        binding.rowAddMusic.setOnClickListener { toast("Add music - belum diimplementasi") }
        binding.rowAddText.setOnClickListener { toast("Add text - belum diimplementasi") }
        binding.btnAddClip.setOnClickListener {
            addClipLauncher.launch(Intent(this, MediaPickerActivity::class.java))
        }
    }

    // ============================== Fullscreen ==============================

    private fun setFullscreen(fullscreen: Boolean) {
        isFullscreen = fullscreen
        val rowsToHide = listOf(
            binding.btnClose, binding.btnExport, binding.btnMore,
            binding.transportRow, binding.txtDuration, binding.rulerFrame,
            binding.viewPlayhead, binding.clipStripRow, binding.rowAddMusic, binding.rowAddText
        )
        rowsToHide.forEach { it.visibility = if (fullscreen) View.INVISIBLE else View.VISIBLE }
        binding.btnExitFullscreen.visibility = if (fullscreen) View.VISIBLE else View.GONE

        val params = binding.previewContainer.layoutParams as ConstraintLayout.LayoutParams
        if (fullscreen) {
            params.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            params.topToBottom = ConstraintLayout.LayoutParams.UNSET
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomToTop = ConstraintLayout.LayoutParams.UNSET
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            params.topMargin = 0
        } else {
            params.height = ConstraintLayout.LayoutParams.WRAP_CONTENT
            params.constrainedHeight = true
            params.topToTop = ConstraintLayout.LayoutParams.UNSET
            params.topToBottom = binding.btnClose.id
            params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            params.bottomToTop = binding.guidePreviewMax.id
            params.verticalBias = 0.0f
            params.topMargin = (8 * resources.displayMetrics.density).toInt()
        }
        binding.previewContainer.layoutParams = params
    }

    // ============================== Loading clips ==============================

    private fun loadClips(uris: List<Uri>) {
        pausePlayback()
        clips.clear()
        uris.forEach { uri ->
            val isVideo = isVideoUri(uri)
            val duration = if (isVideo) readVideoDuration(uri) else DEFAULT_PHOTO_DURATION_MS
            clips.add(ClipItem(uri, isVideo, duration))
        }
        recomputeOffsets()
        buildRuler()
        buildClipStrip()
        currentClipIndex = 0
        positionInClipMs = 0L
        showClip(0)
        updateTimelineUi()
    }

    private fun isVideoUri(uri: Uri): Boolean {
        val type = contentResolver.getType(uri) ?: ""
        return type.startsWith("video")
    }

    private fun readVideoDuration(uri: Uri): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: DEFAULT_PHOTO_DURATION_MS
            retriever.release()
            if (duration > 0) duration else DEFAULT_PHOTO_DURATION_MS
        } catch (e: Exception) {
            DEFAULT_PHOTO_DURATION_MS
        }
    }

    private fun recomputeOffsets() {
        clipStartOffsetsMs.clear()
        var running = 0L
        clips.forEach { clip ->
            clipStartOffsetsMs.add(running)
            running += clip.durationMs
        }
        totalDurationMs = running
    }

    // ============================== Preview ==============================

    private fun showClip(index: Int) {
        if (index !in clips.indices) return
        currentClipIndex = index
        positionInClipMs = 0L
        val clip = clips[index]

        if (clip.isVideo) {
            binding.imgPreview.visibility = View.GONE
            binding.videoPreview.visibility = View.VISIBLE
            binding.videoPreview.setVideoURI(clip.uri)
            binding.videoPreview.setOnPreparedListener { mp ->
                mp.isLooping = false
                currentMediaPlayer = mp
                val level = binding.seekVolume.progress / 100f
                mp.setVolume(level, level)

                val seekMs = pendingSeekMs
                pendingSeekMs = null

                if (isPlaying) {
                    if (seekMs != null) binding.videoPreview.seekTo(seekMs.toInt())
                    binding.videoPreview.start()
                } else {
                    // FIX: VideoView tetap tampil hitam polos kalau cuma dipanggil seekTo()
                    // tanpa pernah start() sekali - ini bug lama Android VideoView, frame
                    // pertama tidak sempat di-render ke surface. Trik yang aman: start()
                    // sebentar lalu langsung pause(), baru seekTo() ke posisi yang benar.
                    binding.videoPreview.start()
                    binding.videoPreview.pause()
                    binding.videoPreview.seekTo(seekMs?.toInt() ?: 1)
                }
            }
            binding.videoPreview.setOnCompletionListener {
                goToClip(currentClipIndex + 1, autoPlay = isPlaying)
            }
        } else {
            binding.videoPreview.visibility = View.GONE
            binding.imgPreview.visibility = View.VISIBLE
            Glide.with(this).load(clip.uri).into(binding.imgPreview)
        }
        highlightActiveClipTile(index)
    }

    private fun goToClip(index: Int, autoPlay: Boolean) {
        if (index !in clips.indices) {
            pausePlayback()
            return
        }
        showClip(index)
        if (autoPlay) startPlayback()
        updateTimelineUi()
    }

    // ============================== Ruler ==============================

    private fun buildRuler() {
        val container = binding.rulerTicksContainer
        container.removeAllViews()
        val totalSeconds = Math.ceil(totalDurationMs / 1000.0).toInt().coerceAtLeast(1)
        val tickWidthPx = (SECOND_WIDTH_DP * resources.displayMetrics.density).toInt()

        for (sec in 0..totalSeconds) {
            if (sec % 2 == 0) {
                val label = TextView(this).apply {
                    text = formatTime(sec * 1000L)
                    setTextColor(getColor(R.color.editor_ruler_label))
                    textSize = 11f
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        tickWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                container.addView(label)
            } else {
                val dot = View(this).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        tickWidthPx, (2 * resources.displayMetrics.density).toInt()
                    )
                    setBackgroundColor(getColor(R.color.editor_ruler_tick))
                }
                container.addView(dot)
            }
        }
    }

    // ============================== Clip strip ==============================

    private val clipTileViews = mutableListOf<ImageView>()

    private fun buildClipStrip() {
        val container = binding.clipStripContainer
        container.removeAllViews()
        clipTileViews.clear()

        // Cover tile (fungsinya menyusul di update berikutnya) + label kecil di bawahnya.
        // FIXED di binding.coverArea (bukan ikut men-scroll bersama ruler), supaya perhitungan
        // padding pusat-playhead di applyCenterPadding() tetap akurat.
        val coverArea = binding.coverArea
        coverArea.removeAllViews()

        val coverColumn = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val coverTile = layoutInflater.inflate(R.layout.item_timeline_clip, coverColumn, false)
        val coverThumb = coverTile.findViewById<ImageView>(R.id.imgClipThumb)
        coverThumb.setImageResource(R.drawable.ic_image)
        coverThumb.scaleType = ImageView.ScaleType.CENTER_INSIDE
        coverTile.setOnClickListener { toast("Cover - fitur ini menyusul di update berikutnya") }
        val coverLabel = TextView(this).apply {
            text = getString(R.string.cover)
            textSize = 9f
            setTextColor(getColor(R.color.text_white))
            gravity = android.view.Gravity.CENTER
        }
        coverColumn.addView(coverTile)
        coverColumn.addView(coverLabel)
        coverArea.addView(coverColumn)

        // Draggable divider handle between the cover and the clip thumbnails.
        val divider = View(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                (10 * resources.displayMetrics.density).toInt(),
                (20 * resources.displayMetrics.density).toInt()
            )
            setBackgroundResource(R.drawable.bg_clip_divider_handle)
        }
        installDividerDrag(divider)
        coverArea.addView(divider)

        // One tile per whole second of each clip (mendekati tampilan di screenshot).
        clips.forEach { clip ->
            val tiles = Math.ceil(clip.durationMs / 1000.0).toInt().coerceAtLeast(1)
            repeat(tiles) {
                val tile = layoutInflater.inflate(R.layout.item_timeline_clip, container, false)
                val thumb = tile.findViewById<ImageView>(R.id.imgClipThumb)
                Glide.with(this).load(clip.uri).centerCrop().into(thumb)
                container.addView(tile)
                clipTileViews.add(thumb)
            }
        }

        applyCenterPadding()
    }

    /** Memberi indikasi visual sederhana klip mana yang sedang aktif di preview. */
    private fun highlightActiveClipTile(clipIndex: Int) {
        // Placeholder ringan: cukup gulir strip ke area klip yang aktif.
        val tilesBeforeThisClip = (0 until clipIndex).sumOf {
            Math.ceil(clips[it].durationMs / 1000.0).toInt().coerceAtLeast(1)
        }
        val tileWidthPx = (46 * resources.displayMetrics.density).toInt()
        binding.scrollClips.post {
            binding.scrollClips.smoothScrollTo(tilesBeforeThisClip * tileWidthPx, 0)
        }
    }

    private fun installDividerDrag(divider: View) {
        var dragStartX = 0f
        var viewStartX = 0f
        divider.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX
                    viewStartX = v.translationX
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val delta = event.rawX - dragStartX
                    val maxRange = 60 * resources.displayMetrics.density
                    v.translationX = (viewStartX + delta).coerceIn(-maxRange, maxRange)
                    true
                }
                else -> true
            }
        }
    }

    // ============================== Scroll sync (ruler <-> clip strip) ==============================

    private fun setupScrollSync() {
        // Playhead diam di tengah layar. Supaya detik 00:00 sampai detik terakhir bisa
        // digeser pas ke bawah garis itu, ruler & clip strip diberi padding kiri/kanan
        // sebesar setengah lebar area yang terlihat. Dihitung sekali layout selesai.
        binding.scrollRuler.post {
            if (binding.scrollRuler.width > 0) {
                centerPaddingPx = binding.scrollRuler.width / 2
                applyCenterPadding()
            }
        }

        binding.scrollRuler.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            if (isSyncingScroll) return@setOnScrollChangeListener
            isSyncingScroll = true
            binding.scrollClips.scrollTo(scrollX, 0)
            isSyncingScroll = false
            updateSideTranslation(scrollX)
            if (!isProgrammaticScroll) onUserScrub(scrollX)
        }
        binding.scrollClips.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            if (isSyncingScroll) return@setOnScrollChangeListener
            isSyncingScroll = true
            binding.scrollRuler.scrollTo(scrollX, 0)
            isSyncingScroll = false
            updateSideTranslation(scrollX)
            if (!isProgrammaticScroll) onUserScrub(scrollX)
        }
    }

    /** Set padding kiri/kanan ruler & clip strip supaya keduanya align di bawah playhead
     *  yang sama-sama diam di tengah layar (lihat catatan di setupScrollSync()). */
    private fun applyCenterPadding() {
        if (centerPaddingPx <= 0) return
        binding.rulerTicksContainer.setPaddingRelative(centerPaddingPx, 0, centerPaddingPx, 0)

        // scrollClips (viewport klip) tidak selebar layar penuh seperti scrollRuler - ada
        // tombol volume + area cover di sebelah kirinya. Offset itu dikurangkan supaya tile
        // detik-ke-0 tetap jatuh di X yang sama dengan tick 00:00 pada ruler.
        val clipsLeftOffset = binding.scrollClips.left
        val clipStartPad = (centerPaddingPx - clipsLeftOffset).coerceAtLeast(0)
        binding.clipStripContainer.setPaddingRelative(clipStartPad, 0, centerPaddingPx, 0)
    }

    /** Dipanggil saat USER menggeser ruler/clip strip dengan jari (bukan saat playback
     *  jalan otomatis). Memindahkan preview & label waktu ke detik yang sekarang persis
     *  ada di bawah playhead tengah. */
    private fun updateSideTranslation(scrollX: Int) {
        val transX = -scrollX.toFloat()
        binding.btnVolume.translationX = transX
        binding.coverArea.translationX = transX
        binding.musicContentGroup.translationX = transX
        binding.textContentGroup.translationX = transX
    }

    private fun onUserScrub(scrollX: Int) {
        if (clips.isEmpty() || totalDurationMs <= 0L) return
        pausePlayback()
        val secondWidthPx = SECOND_WIDTH_DP * resources.displayMetrics.density
        val globalMs = ((scrollX / secondWidthPx) * 1000).toLong().coerceIn(0, totalDurationMs)
        seekToGlobalPosition(globalMs)
    }

    /** Pindah preview + posisi klip aktif ke waktu global tertentu (ms dari awal timeline). */
    private fun seekToGlobalPosition(globalMs: Long) {
        val clamped = globalMs.coerceIn(0, totalDurationMs)
        var idx = clips.lastIndex.coerceAtLeast(0)
        for (i in clips.indices) {
            val start = clipStartOffsetsMs.getOrElse(i) { 0L }
            val end = start + clips[i].durationMs
            if (clamped < end) { idx = i; break }
        }
        val clip = clips.getOrNull(idx) ?: return
        val offset = clipStartOffsetsMs.getOrElse(idx) { 0L }
        val posInClip = (clamped - offset).coerceIn(0, clip.durationMs)

        if (idx != currentClipIndex) {
            pendingSeekMs = if (clip.isVideo) posInClip else null
            showClip(idx)
        } else if (clip.isVideo) {
            binding.videoPreview.seekTo(posInClip.toInt())
        }
        positionInClipMs = posInClip
        binding.txtDuration.text = "${formatTime(clamped)} / ${formatTime(totalDurationMs)}"
    }

    // ============================== Timeline UI (label + playhead) ==============================

    private fun updateTimelineUi() {
        val offset = clipStartOffsetsMs.getOrElse(currentClipIndex) { 0L }
        val globalPositionMs = offset + positionInClipMs
        binding.txtDuration.text =
            "${formatTime(globalPositionMs)} / ${formatTime(totalDurationMs)}"

        val secondWidthPx = SECOND_WIDTH_DP * resources.displayMetrics.density
        val targetScrollX = ((globalPositionMs / 1000.0) * secondWidthPx).toInt()
        isProgrammaticScroll = true
        binding.scrollRuler.scrollTo(targetScrollX, 0)
        binding.scrollClips.scrollTo(targetScrollX, 0)
        updateSideTranslation(targetScrollX)
        isProgrammaticScroll = false
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
