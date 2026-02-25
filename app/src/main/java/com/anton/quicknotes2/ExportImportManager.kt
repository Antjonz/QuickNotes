package com.anton.quicknotes2

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.anton.quicknotes2.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object ExportImportManager {

    // ── Size estimation ───────────────────────────────────────────────────────

    /** Estimate the size of the JSON-only export in bytes. */
    suspend fun estimateJsonSize(db: NoteDatabase): Long = withContext(Dispatchers.IO) {
        buildJson(db, includeImages = false, context = null).toString().toByteArray().size.toLong()
    }

    /** Estimate the extra bytes added if images are included as Base64. */
    suspend fun estimateImagesSize(db: NoteDatabase, context: Context): Long =
        withContext(Dispatchers.IO) {
            var total = 0L
            // Note body images
            db.noteImageDao().getAllImagesDirect().forEach { img ->
                total += encodedSize(context, img.uri)
            }
            // Icon images for all entity types
            val allIconUris = mutableListOf<String?>()
            db.noteDao().getAllNotesAllLevelsDirect().forEach { allIconUris += it.iconUri }
            db.folderDao().getAllFoldersAllLevelsDirect().forEach { allIconUris += it.iconUri }
            db.whiteboardDao().getAllWhiteboardsAllLevelsDirect().forEach { allIconUris += it.iconUri }
            db.noteListDao().getAllListsAllLevelsDirect().forEach { allIconUris += it.iconUri }
            allIconUris.forEach { uri ->
                if (uri != null && !uri.startsWith("color:")) total += encodedSize(context, uri)
            }
            total
        }

    private fun encodedSize(context: Context, uri: String): Long {
        return try {
            val bytes = context.contentResolver.openInputStream(Uri.parse(uri))?.readBytes() ?: return 0L
            (bytes.size * 4L / 3L) + 4L
        } catch (_: Exception) { 0L }
    }

    // ── Export ────────────────────────────────────────────────────────────────

    suspend fun export(
        db: NoteDatabase,
        outputStream: OutputStream,
        includeImages: Boolean,
        context: Context
    ): Unit = withContext(Dispatchers.IO) {
        val json = buildJson(db, includeImages, context)
        outputStream.bufferedWriter().use { it.write(json.toString(2)) }
    }

    /** Encode an iconUri to base64 if it's an image (not a color: value). Returns null if not applicable. */
    private fun encodeIconToBase64(context: Context?, uri: String?): String? {
        if (uri == null || uri.startsWith("color:") || context == null) return null
        return try {
            val bytes = context.contentResolver.openInputStream(Uri.parse(uri))?.readBytes() ?: return null
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Exception) { null }
    }

    private suspend fun buildJson(
        db: NoteDatabase,
        includeImages: Boolean,
        context: Context?
    ): JSONObject {
        val root = JSONObject()
        root.put("version", 2)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("includesImages", includeImages)

        // Folders
        val foldersArr = JSONArray()
        db.folderDao().getAllFoldersAllLevelsDirect().forEach { f ->
            foldersArr.put(JSONObject().apply {
                put("id", f.id); put("name", f.name); put("timestamp", f.timestamp)
                put("sortOrder", f.sortOrder); putOpt("iconUri", f.iconUri)
                putOpt("parentFolderId", f.parentFolderId); putOpt("labelColor", f.labelColor)
                if (includeImages) putOpt("iconBase64", encodeIconToBase64(context, f.iconUri))
            })
        }
        root.put("folders", foldersArr)

        // Notes + images
        val notesArr = JSONArray()
        db.noteDao().getAllNotesAllLevelsDirect().forEach { note ->
            val images = JSONArray()
            db.noteImageDao().getImagesForNoteDirect(note.id).forEach { img ->
                val imgObj = JSONObject().apply {
                    put("id", img.id); put("noteId", img.noteId)
                    put("uri", img.uri); put("sortOrder", img.sortOrder)
                }
                if (includeImages && context != null) {
                    try {
                        val bytes = context.contentResolver.openInputStream(Uri.parse(img.uri))?.readBytes()
                        if (bytes != null) imgObj.put("base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    } catch (_: Exception) {}
                }
                images.put(imgObj)
            }
            notesArr.put(JSONObject().apply {
                put("id", note.id); put("title", note.title); put("body", note.body)
                put("timestamp", note.timestamp); put("sortOrder", note.sortOrder)
                putOpt("folderId", note.folderId); putOpt("iconUri", note.iconUri)
                putOpt("labelColor", note.labelColor)
                if (includeImages) putOpt("iconBase64", encodeIconToBase64(context, note.iconUri))
                put("images", images)
            })
        }
        root.put("notes", notesArr)

        // Whiteboards
        val wbsArr = JSONArray()
        db.whiteboardDao().getAllWhiteboardsAllLevelsDirect().forEach { wb ->
            wbsArr.put(JSONObject().apply {
                put("id", wb.id); put("title", wb.title); put("strokesJson", wb.strokesJson)
                put("timestamp", wb.timestamp); put("sortOrder", wb.sortOrder)
                putOpt("folderId", wb.folderId); putOpt("iconUri", wb.iconUri)
                putOpt("labelColor", wb.labelColor)
                if (includeImages) putOpt("iconBase64", encodeIconToBase64(context, wb.iconUri))
            })
        }
        root.put("whiteboards", wbsArr)

        // Lists + items
        val listsArr = JSONArray()
        db.noteListDao().getAllListsAllLevelsDirect().forEach { l ->
            val itemsArr = JSONArray()
            db.noteListDao().getItemsForListDirect(l.id).forEach { item ->
                itemsArr.put(JSONObject().apply {
                    put("id", item.id); put("listId", item.listId); put("text", item.text)
                    put("position", item.position); put("checked", item.checked)
                })
            }
            listsArr.put(JSONObject().apply {
                put("id", l.id); put("title", l.title); put("timestamp", l.timestamp)
                put("sortOrder", l.sortOrder); putOpt("folderId", l.folderId)
                putOpt("iconUri", l.iconUri); putOpt("labelColor", l.labelColor)
                if (includeImages) putOpt("iconBase64", encodeIconToBase64(context, l.iconUri))
                put("items", itemsArr)
            })
        }
        root.put("lists", listsArr)

        // Dividers
        val dividersArr = JSONArray()
        db.dividerDao().getAllDividersAllLevelsDirect().forEach { d ->
            dividersArr.put(JSONObject().apply {
                put("id", d.id); put("label", d.label)
                put("sortOrder", d.sortOrder); putOpt("folderId", d.folderId)
            })
        }
        root.put("dividers", dividersArr)

        return root
    }

    // ── Import ────────────────────────────────────────────────────────────────

    suspend fun import(
        db: NoteDatabase,
        inputStream: InputStream,
        clearExisting: Boolean,
        context: Context
    ) = withContext(Dispatchers.IO) {
        val text = inputStream.bufferedReader().readText()
        val root = try { JSONObject(text) } catch (e: Exception) {
            throw Exception("Invalid backup file: ${e.message}")
        }

        db.withTransaction {
            if (clearExisting) {
                db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
                db.noteListDao().deleteAll()
                db.whiteboardDao().deleteAll()
                db.noteImageDao().deleteAll()
                db.noteDao().deleteAll()
                db.dividerDao().deleteAll()
                db.folderDao().deleteAll()
                db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
            }

            // Folders — insert parents before children
            val folderIdMap = mutableMapOf<Int, Int>()
            val foldersArr = root.optJSONArray("folders") ?: JSONArray()
            val folderList = (0 until foldersArr.length()).map { foldersArr.getJSONObject(it) }
            val sortedFolders = topologicallySorted(folderList)
            sortedFolders.forEach { obj ->
                val oldId = obj.optInt("id", -1).takeIf { it != -1 } ?: return@forEach
                val oldParent = obj.optInt("parentFolderId", -1).takeIf { it != -1 }
                val newParent = if (oldParent != null) folderIdMap[oldParent] else null
                val newId = db.folderDao().insertAndGetId(
                    Folder(
                        id = 0,
                        name = obj.optString("name", "Folder"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        sortOrder = obj.optInt("sortOrder", 0),
                        iconUri = resolveIconUri(context, obj, prefix = "folder_icon"),
                        parentFolderId = newParent,
                        labelColor = obj.optString("labelColor").ifEmpty { null }
                    )
                ).toInt()
                folderIdMap[oldId] = newId
            }

            // Notes
            val notesArr = root.optJSONArray("notes") ?: JSONArray()
            for (i in 0 until notesArr.length()) {
                val obj = notesArr.getJSONObject(i)
                val oldFolder = obj.optInt("folderId", -1).takeIf { it != -1 }
                val newNoteId = db.noteDao().insertAndGetId(
                    Note(
                        id = 0,
                        title = obj.optString("title", ""),
                        body = obj.optString("body", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        folderId = if (oldFolder != null) folderIdMap[oldFolder] else null,
                        sortOrder = obj.optInt("sortOrder", 0),
                        iconUri = resolveIconUri(context, obj, prefix = "note_icon"),
                        labelColor = obj.optString("labelColor").ifEmpty { null }
                    )
                ).toInt()

                val images = obj.optJSONArray("images") ?: JSONArray()
                for (j in 0 until images.length()) {
                    val imgObj = images.getJSONObject(j)
                    val b64 = imgObj.optString("base64").ifEmpty { null }
                    val resolvedUri = if (b64 != null) {
                        try {
                            val bytes = Base64.decode(b64, Base64.NO_WRAP)
                            val file = File(context.filesDir,
                                "img_${newNoteId}_${imgObj.optInt("sortOrder", j)}_${System.currentTimeMillis()}.jpg")
                            file.writeBytes(bytes)
                            Uri.fromFile(file).toString()
                        } catch (_: Exception) { imgObj.optString("uri", "") }
                    } else imgObj.optString("uri", "")
                    if (resolvedUri.isNotEmpty()) {
                        db.noteImageDao().insert(
                            NoteImage(id = 0, noteId = newNoteId,
                                uri = resolvedUri, sortOrder = imgObj.optInt("sortOrder", j))
                        )
                    }
                }
            }

            // Whiteboards
            val wbsArr = root.optJSONArray("whiteboards") ?: JSONArray()
            for (i in 0 until wbsArr.length()) {
                val obj = wbsArr.getJSONObject(i)
                val oldFolder = obj.optInt("folderId", -1).takeIf { it != -1 }
                db.whiteboardDao().insertAndGetId(
                    Whiteboard(
                        id = 0,
                        title = obj.optString("title", ""),
                        strokesJson = obj.optString("strokesJson", "[]"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        folderId = if (oldFolder != null) folderIdMap[oldFolder] else null,
                        sortOrder = obj.optInt("sortOrder", 0),
                        iconUri = resolveIconUri(context, obj, prefix = "wb_icon"),
                        labelColor = obj.optString("labelColor").ifEmpty { null }
                    )
                )
            }

            // Lists
            val listsArr = root.optJSONArray("lists") ?: JSONArray()
            for (i in 0 until listsArr.length()) {
                val obj = listsArr.getJSONObject(i)
                val oldFolder = obj.optInt("folderId", -1).takeIf { it != -1 }
                val newListId = db.noteListDao().insertAndGetId(
                    NoteList(
                        id = 0,
                        title = obj.optString("title", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        folderId = if (oldFolder != null) folderIdMap[oldFolder] else null,
                        sortOrder = obj.optInt("sortOrder", 0),
                        iconUri = resolveIconUri(context, obj, prefix = "list_icon"),
                        labelColor = obj.optString("labelColor").ifEmpty { null }
                    )
                ).toInt()
                val items = obj.optJSONArray("items") ?: JSONArray()
                for (j in 0 until items.length()) {
                    val it2 = items.getJSONObject(j)
                    val checked = it2.optBoolean("checked", false)
                    db.noteListDao().insertItem(
                        NoteListItem(id = 0, listId = newListId,
                            text = it2.optString("text", ""),
                            position = it2.optInt("position", j), checked = checked)
                    )
                }
            }

            // Dividers
            val dividersArr = root.optJSONArray("dividers") ?: JSONArray()
            for (i in 0 until dividersArr.length()) {
                val obj = dividersArr.getJSONObject(i)
                val oldFolder = obj.optInt("folderId", -1).takeIf { it != -1 }
                db.dividerDao().insert(
                    com.anton.quicknotes2.data.Divider(
                        id = 0, label = obj.optString("label", ""),
                        folderId = if (oldFolder != null) folderIdMap[oldFolder] else null,
                        sortOrder = obj.optInt("sortOrder", 0)
                    )
                )
            }
        }
    }

    /**
     * Resolve an iconUri from a JSON object.
     * - If "iconBase64" is present: decode to a local file and return its URI.
     * - If "iconUri" starts with "color:": return it as-is.
     * - Otherwise: return the raw "iconUri" string (may be stale on a different device,
     *   but is the best we can do without images included in the export).
     */
    private fun resolveIconUri(context: Context, obj: JSONObject, prefix: String): String? {
        val rawUri = obj.optString("iconUri").ifEmpty { null }
        // color: values need no decoding
        if (rawUri != null && rawUri.startsWith("color:")) return rawUri

        val b64 = obj.optString("iconBase64").ifEmpty { null }
        if (b64 != null) {
            return try {
                val bytes = Base64.decode(b64, Base64.NO_WRAP)
                val file = File(context.filesDir, "${prefix}_${System.currentTimeMillis()}.jpg")
                file.writeBytes(bytes)
                Uri.fromFile(file).toString()
            } catch (_: Exception) { rawUri }
        }
        return rawUri
    }

    /** Sort folders so parents always appear before their children. */
    private fun topologicallySorted(folders: List<JSONObject>): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        val remaining = folders.toMutableList()
        val inserted = mutableSetOf<Int>()
        val queue = ArrayDeque<JSONObject>()
        remaining.filter { it.optInt("parentFolderId", -1) == -1 }.forEach { queue.add(it) }
        remaining.removeAll { it.optInt("parentFolderId", -1) == -1 }
        while (queue.isNotEmpty()) {
            val f = queue.removeFirst()
            result.add(f); inserted.add(f.getInt("id"))
            val fId = f.getInt("id")
            val children = remaining.filter { it.optInt("parentFolderId", -1) == fId }
            children.forEach { queue.add(it) }
            remaining.removeAll { it.optInt("parentFolderId", -1) == fId }
        }
        result.addAll(remaining)
        return result
    }

    fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024.0))} MB"
    }
}
