package com.wac.worktrack

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

/** Shows the device's images and videos (MediaStore) once the user grants media access. */
class GalleryFragment : Fragment() {

    data class Media(val uri: Uri, val name: String, val date: Long, val isVideo: Boolean)

    private val items = mutableListOf<Media>()
    private lateinit var adapter: MediaAdapter
    private lateinit var grid: RecyclerView
    private lateinit var permo: View
    private lateinit var syncBtn: Button
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private fun prefs() = requireContext().getSharedPreferences("worktrack", android.content.Context.MODE_PRIVATE)
    private fun serverUrl(): String = prefs().getString("server_url", "") ?: ""

    private val requestPerms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshOrPrompt()
            // Auto-sync images right after access is granted.
            if (hasPerms()) syncAll()
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_gallery, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        grid = view.findViewById(R.id.grid)
        permo = view.findViewById(R.id.permo)
        adapter = MediaAdapter(items) { openMedia(it) }
        grid.layoutManager = GridLayoutManager(requireContext(), 3)
        grid.adapter = adapter
        view.findViewById<View>(R.id.grant).setOnClickListener { requestPerms.launch(neededPerms()) }

        syncBtn = view.findViewById(R.id.sync)
        syncBtn.setOnClickListener { syncAll() }
        syncBtn.setOnLongClickListener { editServerUrl(); true }

        refreshOrPrompt()
    }

    private fun editServerUrl() {
        val input = EditText(requireContext()).apply {
            hint = "http://192.168.1.x:3000"
            setText(serverUrl())
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Dashboard server URL")
            .setMessage("Your PC's address on the same Wi-Fi (shown when you start the server).")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                prefs().edit().putString("server_url", input.text.toString().trim()).apply()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun syncAll() {
        val url = serverUrl()
        if (url.isBlank()) {
            Toast.makeText(requireContext(), "Long-press Sync to set the server URL first", Toast.LENGTH_LONG).show()
            editServerUrl()
            return
        }
        val snapshot = items.filter { !it.isVideo }   // images only for now
        if (snapshot.isEmpty()) {
            Toast.makeText(requireContext(), "No images to sync", Toast.LENGTH_SHORT).show()
            return
        }
        val device = android.os.Build.MODEL ?: "device"
        syncBtn.isEnabled = false
        syncBtn.text = "Syncing 0/${snapshot.size}…"
        io.execute {
            var ok = 0
            var done = 0
            for (m in snapshot) {
                if (MediaUploader.upload(requireContext(), url, m.uri, m.name, device)) ok++
                done++
                val d = done
                ui.post { if (isAdded) syncBtn.text = "Syncing $d/${snapshot.size}…" }
            }
            val okCount = ok
            ui.post {
                if (!isAdded) return@post
                syncBtn.isEnabled = true
                syncBtn.text = "Sync images"
                Toast.makeText(requireContext(), "Uploaded $okCount/${snapshot.size} images", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun neededPerms(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)   // images only for now
        else
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun hasPerms(): Boolean = neededPerms().all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun refreshOrPrompt() {
        if (!hasPerms()) {
            permo.visibility = View.VISIBLE
            grid.visibility = View.GONE
            syncBtn.visibility = View.GONE
            return
        }
        permo.visibility = View.GONE
        grid.visibility = View.VISIBLE
        syncBtn.visibility = View.VISIBLE
        loadMedia()
    }

    private fun loadMedia() {
        items.clear()
        queryStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false)
        // Videos omitted for now (images-only). Re-add Video query when needed.
        items.sortByDescending { it.date }
        adapter.notifyDataSetChanged()
    }

    private fun queryStore(collection: Uri, isVideo: Boolean) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        requireContext().contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                items.add(
                    Media(
                        uri = ContentUris.withAppendedId(collection, id),
                        name = c.getString(nameCol) ?: "",
                        date = c.getLong(dateCol),
                        isVideo = isVideo,
                    ),
                )
            }
        }
    }

    private fun openMedia(media: Media) {
        if (media.isVideo) {
            val video = VideoView(requireContext()).apply {
                setVideoURI(media.uri)
                val mc = MediaController(requireContext())
                mc.setAnchorView(this)
                setMediaController(mc)
            }
            val box = FrameLayout(requireContext()).apply {
                addView(
                    video,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        (resources.displayMetrics.density * 320).toInt(),
                    ),
                )
            }
            AlertDialog.Builder(requireContext())
                .setTitle(media.name)
                .setView(box)
                .setPositiveButton("Close", null)
                .setOnDismissListener { video.stopPlayback() }
                .show()
            video.start()
        } else {
            val image = ImageView(requireContext()).apply {
                setImageURI(media.uri)
                adjustViewBounds = true
            }
            AlertDialog.Builder(requireContext())
                .setTitle(media.name)
                .setView(image)
                .setPositiveButton("Close", null)
                .show()
        }
    }
}

private class MediaAdapter(
    private val items: List<GalleryFragment.Media>,
    private val onOpen: (GalleryFragment.Media) -> Unit,
) : RecyclerView.Adapter<MediaAdapter.VH>() {

    private val executor = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.thumb)
        val badge: TextView = v.findViewById(R.id.badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_media, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = items[position]
        holder.badge.visibility = if (m.isVideo) View.VISIBLE else View.GONE
        holder.thumb.setImageBitmap(null)
        holder.thumb.tag = m.uri
        holder.itemView.setOnClickListener { onOpen(m) }

        val ctx = holder.thumb.context
        executor.execute {
            val bmp = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ctx.contentResolver.loadThumbnail(m.uri, Size(240, 240), null)
                } else null
            } catch (_: Exception) {
                null
            }
            main.post {
                if (holder.thumb.tag == m.uri) {
                    if (bmp != null) holder.thumb.setImageBitmap(bmp)
                    else if (!m.isVideo) holder.thumb.setImageURI(m.uri)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size
}
