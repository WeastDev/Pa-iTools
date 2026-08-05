package com.weastdev.itools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.weastdev.itools.databinding.ActivityMainBinding
import com.weastdev.itools.editor.EditorActivity
import com.weastdev.itools.picker.MediaPickerActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Menangkap hasil pilihan foto/video dari MediaPickerActivity. */
    private val mediaPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uris: ArrayList<Uri>? = result.data
                    ?.getParcelableArrayListExtra(MediaPickerActivity.EXTRA_SELECTED_URIS)
                if (!uris.isNullOrEmpty()) {
                    val intent = Intent(this, EditorActivity::class.java)
                        .putParcelableArrayListExtra(EditorActivity.EXTRA_MEDIA_URIS, uris)
                    startActivity(intent)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    /**
     * Semua tombol sudah terpasang di sini, tapi fungsinya masih placeholder.
     * Nanti tinggal isi satu-satu sesuai kebutuhan (mis. buka kamera, generate AI, dsb).
     */
    private fun setupClickListeners() {
        binding.btnFollow.setOnClickListener {
            toast("Follow - belum diimplementasi")
        }

        binding.btnEdit.setOnClickListener {
            toast("Edit profil - belum diimplementasi")
        }

        binding.cardGenerateVideo.setOnClickListener {
            mediaPickerLauncher.launch(Intent(this, MediaPickerActivity::class.java))
        }

        binding.cardEditImage.setOnClickListener {
            toast("Edit Image - belum diimplementasi")
        }

        binding.navHome.setOnClickListener { toast("Home") }
        binding.navGrid.setOnClickListener { toast("Grid") }
        binding.navWallet.setOnClickListener { toast("Wallet") }
        binding.navProfile.setOnClickListener { toast("Profile") }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
