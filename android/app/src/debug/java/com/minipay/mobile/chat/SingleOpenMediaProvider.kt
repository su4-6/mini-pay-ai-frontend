package com.minipay.mobile.chat

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/** Debug-only provider used to verify that chat media preparation consumes a picked URI once. */
class SingleOpenMediaProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun getType(uri: Uri): String = contentType
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        openCount += 1
        if (openCount > 1) throw FileNotFoundException("URI may only be opened once")
        val file = backingFile ?: throw FileNotFoundException("Test provider is not configured")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    companion object {
        @Volatile private var backingFile: File? = null
        @Volatile private var contentType: String = "application/octet-stream"
        @Volatile var openCount: Int = 0
            private set

        fun configure(file: File, type: String) {
            backingFile = file
            contentType = type
            openCount = 0
        }
    }
}
