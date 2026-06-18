package com.example.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

object SafHelper {
    private const val TAG = "SafHelper"

    data class SafFileItem(
        val name: String,
        val documentId: String,
        val mimeType: String,
        val size: Long,
        val uri: Uri
    )

    fun readTextFromUri(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            } ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error reading text from URI $uri", e)
            ""
        }
    }

    fun writeTextToUri(context: Context, uri: Uri, text: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri, "rwt")?.use { outputStream ->
                outputStream.bufferedWriter().use { it.write(text) }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing text to URI $uri", e)
            false
        }
    }

    fun listChildren(context: Context, parentUri: Uri): List<SafFileItem> {
        val items = mutableListOf<SafFileItem>()
        val treeUri = DocumentsContract.buildDocumentUriUsingTree(
            parentUri,
            DocumentsContract.getTreeDocumentId(parentUri)
        )
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            parentUri,
            DocumentsContract.getDocumentId(treeUri)
        )

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        var cursor: android.database.Cursor? = null
        try {
            cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0)
                    val displayName = cursor.getString(1)
                    val mimeType = cursor.getString(2)
                    val size = cursor.getLong(3)
                    val itemUri = DocumentsContract.buildDocumentUriUsingTree(parentUri, docId)

                    items.add(SafFileItem(displayName, docId, mimeType, size, itemUri))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying child documents under treeUri: $parentUri", e)
        } finally {
            cursor?.close()
        }
        return items
    }

    fun listGrandChildren(context: Context, parentUri: Uri, childDocId: String): List<SafFileItem> {
        val items = mutableListOf<SafFileItem>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            parentUri,
            childDocId
        )

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        var cursor: android.database.Cursor? = null
        try {
            cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0)
                    val displayName = cursor.getString(1)
                    val mimeType = cursor.getString(2)
                    val size = cursor.getLong(3)
                    val itemUri = DocumentsContract.buildDocumentUriUsingTree(parentUri, docId)

                    items.add(SafFileItem(displayName, docId, mimeType, size, itemUri))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying grandchildren documents under treeUri: $parentUri, daughterDocId: $childDocId", e)
        } finally {
            cursor?.close()
        }
        return items
    }

    fun createFile(context: Context, parentTreeUri: Uri, parentDocId: String, displayName: String, mimeType: String): Uri? {
        return try {
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(parentTreeUri, parentDocId)
            DocumentsContract.createDocument(context.contentResolver, parentUri, mimeType, displayName)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating document in tree $parentTreeUri with parent ID $parentDocId", e)
            null
        }
    }
}
