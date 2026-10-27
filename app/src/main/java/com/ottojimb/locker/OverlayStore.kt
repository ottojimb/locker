package com.ottojimb.locker

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.File

/**
 * Persists the overlay source (a single PNG or a folder picked with the document tree picker)
 * and the enabled flag. With a folder, every [load] returns a random image from it.
 */
object OverlayStore {
    private const val TAG = "OverlayStore"
    private const val FILE_NAME = "overlay.png"
    private const val PREFS = "locker"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_FOLDER = "folder"
    private const val KEY_LAST = "last_document"
    private const val MAX_SIDE = 2400
    private val IMAGE_TYPES = setOf("image/png", "image/jpeg", "image/webp")

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, uri: Uri) {
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        }
        tmp.renameTo(file(context))
        setFolder(context, null)
    }

    fun setFolder(context: Context, tree: Uri?) {
        val old = folder(context)
        if (old != null && old != tree) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        if (tree != null) {
            context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        prefs(context).edit().putString(KEY_FOLDER, tree?.toString()).remove(KEY_LAST).apply()
    }

    fun folder(context: Context): Uri? = prefs(context).getString(KEY_FOLDER, null)?.let(Uri::parse)

    fun clear(context: Context) {
        file(context).delete()
        setFolder(context, null)
    }

    /** Image documents in the chosen folder (not recursive). */
    fun folderImages(context: Context): List<Uri> {
        val tree = folder(context) ?: return emptyList()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree),
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        return runCatching {
            context.contentResolver.query(children, projection, null, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        if (c.getString(1) in IMAGE_TYPES) {
                            add(DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)))
                        }
                    }
                }
            }
        }.onFailure { Log.w(TAG, "cannot list folder", it) }.getOrNull().orEmpty()
    }

    /** The single PNG, or a random folder image different from the previous one. Call off the main thread. */
    fun load(context: Context): Bitmap? {
        if (folder(context) == null) {
            val f = file(context)
            return if (f.exists()) decode(context, Uri.fromFile(f)) else null
        }
        val images = folderImages(context)
        if (images.isEmpty()) return null
        val last = prefs(context).getString(KEY_LAST, null)
        val pick = images.filter { it.toString() != last }.ifEmpty { images }.random()
        prefs(context).edit().putString(KEY_LAST, pick.toString()).apply()
        Log.i(TAG, "picked ${pick.lastPathSegment}")
        return decode(context, pick)
    }

    private fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sample
        }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.onFailure { Log.w(TAG, "cannot decode $uri", it) }.getOrNull()

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
