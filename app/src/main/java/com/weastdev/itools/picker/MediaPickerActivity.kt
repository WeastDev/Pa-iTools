package com.weastdev.itools.picker

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.weastdev.itools.R
import com.weastdev.itools.databinding.ActivityMediaPickerBinding

class MediaPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SELECTED_URIS = "extra_selected_uris"
        private const val GRID_SPAN_COUNT = 3
    }

    private lateinit var binding: ActivityMediaPickerBinding

    private lateinit var mediaAdapter: MediaAdapter
    private lateinit var selectedAdapter: SelectedMediaAdapter

    /** Semua media yang berhasil dibaca dari penyimpanan (belum difilter). */
    private val allMedia = mutableListOf<MediaItem>()

    /** Media yang sedang dipilih, urutan insersi = urutan angka yang ditampilkan. */
    private val selectedMedia = LinkedHashMap<Long, MediaItem>()

    private var currentFilter = MediaFilter.ALL

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.any { it }) {
                loadMediaFromStorage()
            } else {
                showEmptyState(true)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGrid()
        setupSelectedStrip()
        setupTopBar()
        setupFilterRow()
        setupImportBar()

        ensurePermissionThenLoad()
    }

    // ============================== Setup ==============================

    private fun setupGrid() {
        mediaAdapter = MediaAdapter(
            getSelectionNumber = { item -> selectionNumberFor(item.id) },
            onItemClick = { item -> toggleSelection(item) }
        )
        binding.recyclerMedia.layoutManager = GridLayoutManager(this, GRID_SPAN_COUNT)
        binding.recyclerMedia.adapter = mediaAdapter

        val spacingPx = (2 * resources.displayMetrics.density).toInt()
        binding.recyclerMedia.addItemDecoration(
            GridSpacingItemDecoration(GRID_SPAN_COUNT, spacingPx)
        )
    }

    private fun setupSelectedStrip() {
        selectedAdapter = SelectedMediaAdapter(onRemove = { item -> removeSelection(item) })
        binding.recyclerSelected.adapter = selectedAdapter
    }

    private fun setupTopBar() {
        binding.btnBack.setOnClickListener { finish() }
        // Cloud & Stock belum diimplementasi - reserved untuk tab lain di masa depan.
    }

    private fun setupFilterRow() {
        binding.btnFilterAll.setOnClickListener { applyFilter(MediaFilter.ALL) }
        binding.btnFilterVideo.setOnClickListener { applyFilter(MediaFilter.VIDEO) }
        binding.btnFilterPhoto.setOnClickListener { applyFilter(MediaFilter.PHOTO) }
        updateFilterButtonStyles()
    }

    private fun setupImportBar() {
        updateImportBarState()
        binding.btnImport.setOnClickListener {
            if (selectedMedia.isEmpty()) return@setOnClickListener
            val result = Intent().apply {
                putParcelableArrayListExtra(
                    EXTRA_SELECTED_URIS,
                    ArrayList(selectedMedia.values.map { it.uri })
                )
            }
            setResult(RESULT_OK, result)
            finish()
        }
    }

    // ============================== Permission & loading ==============================

    private fun ensurePermissionThenLoad() {
        val permissions = requiredMediaPermissions()
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            loadMediaFromStorage()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    private fun requiredMediaPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun loadMediaFromStorage() {
        showEmptyState(false)
        Thread {
            val result = mutableListOf<MediaItem>()
            result.addAll(queryImages())
            result.addAll(queryVideos())
            result.sortByDescending { it.dateAdded }

            runOnUiThread {
                allMedia.clear()
                allMedia.addAll(result)
                applyFilter(currentFilter)
                if (allMedia.isEmpty()) showEmptyState(true)
            }
        }.start()
    }

    private fun queryImages(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )
        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val dateAdded = it.getLong(dateCol)
                val uri: Uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
                items.add(MediaItem(id = id, uri = uri, isVideo = false, dateAdded = dateAdded))
            }
        }
        return items
    }

    private fun queryVideos(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION
        )
        val cursor = contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )
        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dateCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val dateAdded = it.getLong(dateCol)
                val duration = it.getLong(durationCol)
                val uri: Uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                )
                items.add(
                    MediaItem(
                        id = id,
                        uri = uri,
                        isVideo = true,
                        durationMs = duration,
                        dateAdded = dateAdded
                    )
                )
            }
        }
        return items
    }

    // ============================== Filtering ==============================

    private fun applyFilter(filter: MediaFilter) {
        currentFilter = filter
        updateFilterButtonStyles()

        val filtered = when (filter) {
            MediaFilter.ALL -> allMedia
            MediaFilter.VIDEO -> allMedia.filter { it.isVideo }
            MediaFilter.PHOTO -> allMedia.filter { !it.isVideo }
        }
        mediaAdapter.submitList(filtered)
    }

    private fun updateFilterButtonStyles() {
        setToggleStyle(binding.btnFilterAll, currentFilter == MediaFilter.ALL)
        setToggleStyle(binding.btnFilterVideo, currentFilter == MediaFilter.VIDEO)
        setToggleStyle(binding.btnFilterPhoto, currentFilter == MediaFilter.PHOTO)
    }

    private fun setToggleStyle(view: TextView, selected: Boolean) {
        if (selected) {
            view.setBackgroundResource(R.drawable.bg_toggle_white)
            view.setTextColor(ContextCompat.getColor(this, R.color.text_black))
        } else {
            view.setBackgroundResource(R.drawable.bg_toggle_gray)
            view.setTextColor(ContextCompat.getColor(this, R.color.text_white))
        }
    }

    // ============================== Selection ==============================

    private fun selectionNumberFor(id: Long): Int? {
        if (!selectedMedia.containsKey(id)) return null
        // Nomor = posisi insersi (1-based) di dalam LinkedHashMap.
        val index = selectedMedia.keys.toList().indexOf(id)
        return if (index >= 0) index + 1 else null
    }

    private fun toggleSelection(item: MediaItem) {
        if (selectedMedia.containsKey(item.id)) {
            selectedMedia.remove(item.id)
        } else {
            selectedMedia[item.id] = item
        }
        onSelectionChanged()
    }

    private fun removeSelection(item: MediaItem) {
        selectedMedia.remove(item.id)
        onSelectionChanged()
    }

    private fun onSelectionChanged() {
        mediaAdapter.refreshSelectionState()
        selectedAdapter.submitList(selectedMedia.values.toList())
        updateImportBarState()
    }

    private fun updateImportBarState() {
        val hasSelection = selectedMedia.isNotEmpty()

        binding.recyclerSelected.visibility = if (hasSelection) View.VISIBLE else View.GONE
        binding.txtAddClip.visibility = if (hasSelection) View.GONE else View.VISIBLE

        binding.btnImport.isEnabled = hasSelection
        binding.btnImport.setBackgroundResource(
            if (hasSelection) R.drawable.bg_import_active else R.drawable.bg_import_inactive
        )
        binding.btnImport.setTextColor(ContextCompat.getColor(this, R.color.text_black))
    }

    // ============================== Empty state ==============================

    private fun showEmptyState(show: Boolean) {
        binding.txtEmptyState.visibility = if (show) View.VISIBLE else View.GONE
        binding.recyclerMedia.visibility = if (show) View.GONE else View.VISIBLE
    }
}
