# Cara Update Repo Lewat Termux

## Update ke-2 (yang ini)
1. **Tombol + di baris "Add Music"** dipindah ke sebelah kiri teks (urutan:
   ikon nada → tombol + → teks "Add Music"), sebelumnya + ada di paling kanan.
2. **Tombol + di baris "Add Text"** dipindah ke sebelah kiri teks juga
   (ikon "T" → tombol + → teks "Add text"), sama seperti di atas.
3. **Preview foto/video di atas tombol Play tidak muncul** - ini bug klasik
   `VideoView` di Android yang tetap tampil hitam polos sampai videonya
   di-*play* atau di-*seek* dulu. Sekarang begitu video selesai di-load,
   otomatis di-seek ke frame pertama (`seekTo(1)`) supaya langsung kelihatan
   walau belum ditekan Play. Juga ditambahkan `minHeight` pengaman di area
   preview supaya tidak collapse ke 0 kalau ada masalah pengukuran layout.

File yang berubah:
- `app/src/main/res/layout/activity_editor.xml`
- `app/src/main/java/com/weastdev/itools/editor/EditorActivity.kt`

## Update ke-1 (sebelumnya)
Posisi tombol **Play** di transport row (baris tombol di bawah preview)
dikunci persis di tengah pakai ConstraintLayout, bukan LinearLayout+weight
lagi. Sebelumnya tombol Play meleset dari tengah karena lebar grup kiri
(undo/redo) beda sama lebar grup kanan (fullscreen). Tombol chevron
prev/next di kiri-kanan Play juga dihapus karena memang tidak ada di spek
(cuma ada: belok-kiri, belok-kanan, Play, Fullscreen).

## 1. Extract zip ke folder project

```bash
cd ~/storage/downloads
unzip -o Pa-iTools.zip -d Pa-iTools
cd Pa-iTools
```

Kalau ini update ke repo yang **sudah ada** di HP (bukan clone baru), cukup
timpa 2 file yang berubah ke folder repo lokal kamu, lalu lanjut ke langkah 2.

## 2. Commit & push (repo sudah pernah di-init sebelumnya)

```bash
git add -A
git commit -m "fix: posisi tombol play di-center, hapus tombol prev/next yang tidak sesuai desain"
git push origin main
```

## 3. Kalau repo lokal rusak / perlu init ulang (pola yang biasa kamu pakai)

```bash
git init
git remote add origin https://github.com/andinelvira64-bot/Pa-iTools.git
git add -A
git commit -m "fix: posisi tombol play di-center"
git branch -M main
git push -u origin main --force
```
> Ganti URL remote di atas sesuai nama repo GitHub kamu yang sebenarnya
> kalau bukan `Pa-iTools`.

## 4. Pantau build APK

Setelah push, buka tab **Actions** di repo GitHub kamu — workflow
`.github/workflows/build.yml` akan otomatis jalan (trigger: push ke `main`),
dan hasil APK debug bisa diunduh dari bagian **Artifacts** pada run
tersebut begitu selesai (biasanya 2-4 menit).
