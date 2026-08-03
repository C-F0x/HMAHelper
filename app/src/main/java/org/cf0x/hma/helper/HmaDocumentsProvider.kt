package org.cf0x.hma.helper

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

class HmaDocumentsProvider : DocumentsProvider() {

    companion object {
        private const val DEFAULT_ROOT_ID = "0"

        private val DEFAULT_ROOT_COLUMNS = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID
        )
        private val DEFAULT_DOCUMENT_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
        )
    }

    override fun onCreate(): Boolean = true

    /** Export directory exposed by this provider. */
    private val exportRoot: File
        get() = File(context!!.filesDir, "HMA Helper/data/export")

    /** Resolve a document id to a File, refusing anything outside the export root. */
    private fun resolveDocument(documentId: String): File {
        val file = if (documentId == "/" || documentId == DEFAULT_ROOT_ID) exportRoot else File(documentId)
        val normalized = file.canonicalFile
        val rootPath = exportRoot.canonicalPath
        if (!normalized.path.startsWith(rootPath)) {
            throw FileNotFoundException("Invalid document: $documentId")
        }
        return normalized
    }

    override fun queryRoots(projection: Array<String>?): Cursor {
        return MatrixCursor(projection ?: DEFAULT_ROOT_COLUMNS).apply {
            newRow().apply {
                add(DocumentsContract.Root.COLUMN_ROOT_ID, DEFAULT_ROOT_ID)
                add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_LOCAL_ONLY)
                add(DocumentsContract.Root.COLUMN_ICON, R.drawable.ic_launcher_foreground)
                add(DocumentsContract.Root.COLUMN_TITLE, "HMA Helper")
                add(DocumentsContract.Root.COLUMN_SUMMARY, "Config exports")
                add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, "/")
            }
        }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_COLUMNS)
        val parentFile = resolveDocument(parentDocumentId)
        parentFile.takeIf { it.exists() }?.listFiles()?.forEach { file ->
            includeFile(result, file)
        }
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_COLUMNS)
        val file = resolveDocument(documentId)
        if (file.exists()) includeFile(result, file)
        return result
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = resolveDocument(documentId)
        if (!file.exists()) throw FileNotFoundException("$documentId not found")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun includeFile(result: MatrixCursor, file: File) {
        result.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, file.absolutePath)
            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name)
            add(DocumentsContract.Document.COLUMN_SIZE, file.length())
            add(DocumentsContract.Document.COLUMN_FLAGS, 0)
            add(DocumentsContract.Document.COLUMN_MIME_TYPE, getDocumentType(file))
        }
    }

    private fun getDocumentType(file: File): String {
        return if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR
        else "application/json"
    }
}
