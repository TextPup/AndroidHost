package io.loperlabs.textpup.telephony

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream

data class ExtractedMmsPart(
    val partId: String,
    val mimeType: String,
    val name: String,
    val dataBytes: ByteArray,
    val sizeBytes: Long
)

class MmsPartExtractor(private val context: Context) {

    /**
     * Extracts multimedia binary parts (photos, videos, audio) for an MMS message ID.
     */
    fun getPartsForMms(mmsId: String): List<ExtractedMmsPart> {
        val parts = mutableListOf<ExtractedMmsPart>()
        val uri = Uri.parse("content://mms/part")
        val projection = arrayOf("_id", "mid", "ct", "name", "cl", "fn", "_data", "text")
        val selection = "mid = ?"
        val selectionArgs = arrayOf(mmsId)

        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                null
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndex("_id")
                val ctCol = c.getColumnIndex("ct")
                val nameCol = c.getColumnIndex("name")
                val clCol = c.getColumnIndex("cl")
                val fnCol = c.getColumnIndex("fn")
                val textCol = c.getColumnIndex("text")

                while (c.moveToNext()) {
                    val partId = c.getString(idCol)
                    val mimeType = c.getString(ctCol) ?: continue

                    // Ignore SMIL presentation instructions and plain text parts in the binary media list
                    if (mimeType == "application/smil" || mimeType.startsWith("text/")) {
                        continue
                    }

                    val filename = c.getString(nameCol)
                        ?: c.getString(clCol)
                        ?: c.getString(fnCol)
                        ?: "mms_attachment_$partId"

                    val partUri = Uri.parse("content://mms/part/$partId")
                    val bytes = readPartBytes(partUri)

                    if (bytes != null && bytes.isNotEmpty()) {
                        parts.add(
                            ExtractedMmsPart(
                                partId = partId,
                                mimeType = mimeType,
                                name = filename,
                                dataBytes = bytes,
                                sizeBytes = bytes.size.toLong()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return parts
    }

    private fun readPartBytes(partUri: Uri): ByteArray? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(partUri)
            inputStream?.use { input ->
                val byteBuffer = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var len: Int
                while (input.read(buffer).also { len = it } != -1) {
                    byteBuffer.write(buffer, 0, len)
                }
                byteBuffer.toByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }
}
