package com.example.kenyanradiostations

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Where recordings live and what they are called.
 *
 * Files go into Music/Radio Diaspora so every music player and file browser on
 * the device picks them up. On API 29+ that is done through MediaStore (no
 * storage permission needed, and MediaStore de-duplicates names itself); below
 * that it is a plain file plus a media-scanner nudge.
 */
object RecordingStore {

    const val FOLDER_NAME = "Radio Diaspora"

    /** An open recording: the stream to write to, and how to finalise it. */
    class Target(
        val displayName: String,
        val output: OutputStream,
        val uri: Uri?,
        val file: File?
    )

    data class Recording(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val addedAtSeconds: Long
    )

    /**
     * "Citizen FM 91.5 - 2026-09-13 14-32.mp3"
     *
     * Sortable, readable, and free of every character FAT32 and exFAT reject,
     * which matters because these land on removable storage on some devices.
     */
    fun displayName(stationName: String, extension: String, now: Date = Date()): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US).format(now)
        val safe = stationName
            .replace(Regex("[\\\\/:*?\"<>|]"), "-")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.', '-', ' ')
            .take(60)
            .ifBlank { "Recording" }
        return "$safe - $stamp.$extension"
    }

    fun create(
        context: Context,
        stationName: String,
        extension: String,
        mimeType: String
    ): Target? {
        val name = displayName(stationName, extension)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createViaMediaStore(context, name, mimeType, stationName)
        } else {
            createLegacyFile(name)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createViaMediaStore(
        context: Context,
        name: String,
        mimeType: String,
        stationName: String
    ): Target? {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(
                MediaStore.Audio.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MUSIC + File.separator + FOLDER_NAME
            )
            put(MediaStore.Audio.Media.ARTIST, stationName)
            put(MediaStore.Audio.Media.ALBUM, FOLDER_NAME)
            // Hides the file from other apps until it is complete.
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return null
        val output = resolver.openOutputStream(uri)
        if (output == null) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        return Target(name, output, uri, null)
    }

    @Suppress("DEPRECATION")
    private fun createLegacyFile(name: String): Target? {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            FOLDER_NAME
        )
        if (!directory.exists() && !directory.mkdirs()) return null

        var file = File(directory, name)
        var counter = 1
        while (file.exists()) {
            val base = name.substringBeforeLast('.')
            val extension = name.substringAfterLast('.')
            file = File(directory, "$base ($counter).$extension")
            counter++
        }

        return runCatching { Target(file.name, FileOutputStream(file), null, file) }.getOrNull()
    }

    /** Publishes the finished file. */
    fun finish(context: Context, target: Target) {
        runCatching { target.output.close() }
        val uri = target.uri
        if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            runCatching { context.contentResolver.update(uri, values, null, null) }
        }
        target.file?.let { file ->
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
        }
    }

    /** Removes a recording that never got any usable audio. */
    fun discard(context: Context, target: Target) {
        runCatching { target.output.close() }
        target.uri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
        target.file?.let { runCatching { it.delete() } }
    }

    @Suppress("DEPRECATION")
    fun list(context: Context): List<RecordingStore.Recording> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED
        )
        // DATA is deprecated for writing but still populated and queryable, and
        // it is the one predicate that behaves the same on every supported API.
        val selection = "${MediaStore.Audio.Media.DATA} LIKE ?"
        val arguments = arrayOf("%/$FOLDER_NAME/%")
        val order = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val recordings = mutableListOf<RecordingStore.Recording>()
        context.contentResolver
            .query(collection, projection, selection, arguments, order)
            ?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    recordings += RecordingStore.Recording(
                        // withAppendedId rather than the two-argument
                        // getContentUri, which only exists from API 29.
                        uri = ContentUris.withAppendedId(collection, id),
                        displayName = cursor.getString(nameColumn).orEmpty(),
                        sizeBytes = cursor.getLong(sizeColumn),
                        addedAtSeconds = cursor.getLong(dateColumn)
                    )
                }
            }
        return recordings
    }

    fun delete(context: Context, recording: RecordingStore.Recording): Boolean =
        runCatching { context.contentResolver.delete(recording.uri, null, null) > 0 }
            .getOrDefault(false)
}
