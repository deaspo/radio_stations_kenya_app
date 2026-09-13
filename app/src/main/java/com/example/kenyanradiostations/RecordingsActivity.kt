package com.example.kenyanradiostations

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kenyanradiostations.databinding.ActivityRecordingsBinding
import com.example.kenyanradiostations.databinding.ItemRecordingBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything saved in Music/Radio Diaspora, newest first. */
class RecordingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingsBinding

    private val adapter = RecordingAdapter(
        onPlay = { recording -> play(recording) },
        onShare = { recording -> share(recording) },
        onDelete = { recording -> confirmDelete(recording) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBar.updatePadding(top = bars.top)
            binding.recyclerView.updatePadding(
                bottom = bars.bottom + resources.getDimensionPixelSize(R.dimen.list_bottom_padding)
            )
            insets
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        // Refreshed on every entry: a recording may have finished while this
        // screen was in the background.
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val recordings = withContext(Dispatchers.IO) {
                runCatching { RecordingStore.list(this@RecordingsActivity) }.getOrDefault(emptyList())
            }
            adapter.submitList(recordings)
            binding.stateEmpty.visibility = if (recordings.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun play(recording: RecordingStore.Recording) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(recording.uri, "audio/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(this, R.string.recordings_no_player, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Sharing is gated behind a confirmation rather than removed.
     *
     * It is worth being clear-eyed about what this does and does not achieve:
     * recordings live in Music/Radio Diaspora and are indexed in MediaStore, so
     * any file manager on the device can already share them. The dialog does not
     * prevent redistribution - it makes the app stop presenting it as a
     * frictionless one-tap feature, and puts the copyright position in front of
     * the person doing it. See docs/recording-and-copyright.md.
     *
     * Shown every time, deliberately: a "don't ask again" option would undo the
     * only thing this is for.
     */
    private fun share(recording: RecordingStore.Recording) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.recordings_share_confirm_title)
            .setMessage(R.string.recordings_share_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.recordings_share_confirm_action) { _, _ ->
                startShareChooser(recording)
            }
            .show()
    }

    private fun startShareChooser(recording: RecordingStore.Recording) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("audio/*")
            .putExtra(Intent.EXTRA_STREAM, recording.uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, getString(R.string.recordings_share_via)))
    }

    private fun confirmDelete(recording: RecordingStore.Recording) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.recordings_delete_confirm_title)
            .setMessage(getString(R.string.recordings_delete_confirm_message, recording.displayName))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.recordings_delete) { _, _ -> delete(recording) }
            .show()
    }

    private fun delete(recording: RecordingStore.Recording) {
        lifecycleScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                RecordingStore.delete(this@RecordingsActivity, recording)
            }
            Toast.makeText(
                this@RecordingsActivity,
                if (deleted) R.string.recordings_deleted else R.string.recordings_delete_failed,
                Toast.LENGTH_SHORT
            ).show()
            if (deleted) load()
        }
    }

    private class RecordingAdapter(
        private val onPlay: (RecordingStore.Recording) -> Unit,
        private val onShare: (RecordingStore.Recording) -> Unit,
        private val onDelete: (RecordingStore.Recording) -> Unit
    ) : ListAdapter<RecordingStore.Recording, RecordingAdapter.ViewHolder>(DIFF_CALLBACK) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                ItemRecordingBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class ViewHolder(
            private val binding: ItemRecordingBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(recording: RecordingStore.Recording) {
                val context = binding.root.context
                binding.recordingName.text = recording.displayName
                binding.recordingMeta.text = context.getString(
                    R.string.recordings_meta,
                    DateUtils.getRelativeTimeSpanString(
                        recording.addedAtSeconds * 1000L,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    ),
                    Formatter.formatShortFileSize(context, recording.sizeBytes)
                )
                binding.root.setOnClickListener { onPlay(recording) }
                binding.recordingShare.setOnClickListener { onShare(recording) }
                binding.recordingDelete.setOnClickListener { onDelete(recording) }
            }
        }

        companion object {
            val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RecordingStore.Recording>() {
                override fun areItemsTheSame(
                    oldItem: RecordingStore.Recording,
                    newItem: RecordingStore.Recording
                ): Boolean = oldItem.uri == newItem.uri

                override fun areContentsTheSame(
                    oldItem: RecordingStore.Recording,
                    newItem: RecordingStore.Recording
                ): Boolean = oldItem == newItem
            }
        }
    }
}
