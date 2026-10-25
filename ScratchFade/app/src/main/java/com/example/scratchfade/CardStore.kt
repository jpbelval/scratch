package com.example.scratchfade

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/**
 * Keeps the two chosen cards in the app's private storage. Copying the decoded
 * bitmap (rather than relying on a document URI) means selections survive app
 * restarts even when a provider does not offer persistable permissions.
 */
object CardStore {
    private const val DIR = "magic_cards"
    const val SCREEN = "screen"
    const val MESSAGE = "message"

    fun save(context: Context, kind: String, bitmap: Bitmap): Boolean {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val hasAlpha = bitmap.hasAlpha()
        val target = File(dir, kind + if (hasAlpha) ".png" else ".jpg")
        val temp = File(dir, "$kind.tmp")
        return try {
            FileOutputStream(temp).use { out ->
                val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                if (!bitmap.compress(format, 92, out)) {
                    temp.delete()
                    return false
                }
            }
            // Only replace the previous card once the new file is safely written.
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            deleteOtherKinds(dir, kind, target)
            true
        } catch (_: Exception) {
            temp.delete()
            false
        }
    }

    fun load(context: Context, kind: String): Bitmap? {
        val file = find(context, kind) ?: return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    fun clear(context: Context) {
        File(context.filesDir, DIR).listFiles()?.forEach { it.delete() }
    }

    private fun find(context: Context, kind: String): File? {
        val dir = File(context.filesDir, DIR)
        return dir.listFiles { file ->
            file.name.startsWith("$kind.") && file.name != "$kind.tmp"
        }?.firstOrNull()
    }

    private fun deleteOtherKinds(dir: File, kind: String, keep: File) {
        dir.listFiles { file -> file.name.startsWith("$kind.") && file != keep }
            ?.forEach { it.delete() }
    }
}
