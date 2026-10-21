package com.wac.worktrack

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.MediaController
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.text.DateFormat
import java.util.Date

class FilesFragment : Fragment() {

    private val files = mutableListOf<File>()
    private lateinit var adapter: FileAdapter
    private lateinit var empty: TextView

    private val picker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importFile(uri)
        }

    private fun importsDir(): File =
        File(requireContext().filesDir, "imports").apply { mkdirs() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_files, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        empty = view.findViewById(R.id.empty)
        val list = view.findViewById<RecyclerView>(R.id.list)
        adapter = FileAdapter(files, onOpen = { open(it) }, onDelete = { remove(it) })
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        view.findViewById<FloatingActionButton>(R.id.add).setOnClickListener {
            picker.launch(arrayOf("*/*"))
        }
        refresh()
    }

    private fun refresh() {
        files.clear()
        importsDir().listFiles()?.sortedByDescending { it.lastModified() }?.let { files.addAll(it) }
        adapter.notifyDataSetChanged()
        empty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun queryName(uri: Uri): String? {
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
        }
        return null
    }

    private fun uniqueFile(name: String): File {
        var dest = File(importsDir(), name)
        if (!dest.exists()) return dest
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (dest.exists()) { dest = File(importsDir(), "$base ($i)$ext"); i++ }
        return dest
    }

    private fun importFile(uri: Uri) {
        try {
            val name = queryName(uri) ?: "file_${System.currentTimeMillis()}"
            val dest = uniqueFile(name)
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            } ?: run {
                Toast.makeText(requireContext(), "Could not read that file", Toast.LENGTH_SHORT).show()
                return
            }
            refresh()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun open(file: File) {
        val ext = file.extension.lowercase()
        when (ext) {
            in IMAGE_EXT -> { openImage(file); return }
            in VIDEO_EXT -> { openVideo(file); return }
            !in TEXT_EXT -> {
                val size = Formatter.formatShortFileSize(requireContext(), file.length())
                AlertDialog.Builder(requireContext())
                    .setTitle(file.name)
                    .setMessage("$size\n${DateFormat.getDateTimeInstance().format(Date(file.lastModified()))}\n\nNo preview for .$ext files.")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }
        }
        val raw = try { file.readText() } catch (e: Exception) { "Could not read: ${e.message}" }
        val body = if (raw.length > 20000) raw.take(20000) + "\n…(truncated)" else raw
        val tv = TextView(requireContext()).apply {
            text = body
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 13f
            setPadding(48, 32, 48, 32)
        }
        val scroll = ScrollView(requireContext()).apply { addView(tv) }
        AlertDialog.Builder(requireContext())
            .setTitle(file.name)
            .setView(scroll)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun openImage(file: File) {
        val image = ImageView(requireContext()).apply {
            setImageURI(Uri.fromFile(file))
            adjustViewBounds = true
            setPadding(16, 16, 16, 16)
        }
        val scroll = ScrollView(requireContext()).apply { addView(image) }
        AlertDialog.Builder(requireContext())
            .setTitle(file.name)
            .setView(scroll)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun openVideo(file: File) {
        val video = VideoView(requireContext()).apply {
            setVideoURI(Uri.fromFile(file))
            val mc = MediaController(requireContext())
            mc.setAnchorView(this)
            setMediaController(mc)
        }
        val container = FrameLayout(requireContext()).apply {
            addView(
                video,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    (resources.displayMetrics.density * 320).toInt(),
                ),
            )
        }
        AlertDialog.Builder(requireContext())
            .setTitle(file.name)
            .setView(container)
            .setPositiveButton("Close", null)
            .setOnDismissListener { video.stopPlayback() }
            .show()
        video.start()
    }

    private fun remove(file: File) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete file")
            .setMessage("Delete \"${file.name}\"? This removes it from the app only.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                file.delete()
                refresh()
            }
            .show()
    }

    companion object {
        private val TEXT_EXT = setOf("txt", "md", "json", "csv", "log", "xml", "html", "js", "ts", "kt", "java")
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        private val VIDEO_EXT = setOf("mp4", "3gp", "mkv", "webm", "mov")
    }
}

private class FileAdapter(
    private val items: List<File>,
    private val onOpen: (File) -> Unit,
    private val onDelete: (File) -> Unit,
) : RecyclerView.Adapter<FileAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.name)
        val meta: TextView = v.findViewById(R.id.meta)
        val delete: TextView = v.findViewById(R.id.delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val f = items[position]
        holder.name.text = f.name
        val size = Formatter.formatShortFileSize(holder.itemView.context, f.length())
        holder.meta.text = "$size · ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(f.lastModified()))}"
        holder.itemView.setOnClickListener { onOpen(f) }
        holder.delete.setOnClickListener { onDelete(f) }
    }

    override fun getItemCount(): Int = items.size
}
