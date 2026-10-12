package tss.t.tsiptv.core.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class AndroidShareManager(private val context: Context) : ShareManager {
    override fun shareTextAndImage(title: String, text: String, imageBytes: ByteArray?) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (imageBytes != null) "image/png" else "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (imageBytes != null) {
                try {
                    val cachePath = File(context.cacheDir, "images")
                    cachePath.mkdirs() // don't forget to make the directory
                    val stream = FileOutputStream(cachePath.toString() + "/share_image.png")
                    stream.write(imageBytes)
                    stream.close()

                    val imagePath = File(context.cacheDir, "images")
                    val newFile = File(imagePath, "share_image.png")
                    val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", newFile)

                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        val chooser = Intent.createChooser(shareIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
