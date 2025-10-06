package org.anonymous.spineviewer.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import org.anonymous.spineviewer.Main
import org.anonymous.spineviewer.Platform
import java.io.FileOutputStream

/** Launches the Android application.  */
const val IMPORT_FILES = 42

class AndroidLauncher() : AndroidApplication(), Platform {
    private var currentFile = ""
    private var currentBackground: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val configuration = AndroidApplicationConfiguration()
        configuration.useImmersiveMode = true // Recommended, but not required.
        initialize(Main(this), configuration)
    }

    override fun getCurrentFile(): String = currentFile
    override fun getCurrentBackground(): String? = currentBackground

    override fun importFiles() = startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        setType("*/*")
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        putExtra(
            Intent.EXTRA_MIME_TYPES, arrayOf("image/png", "image/jpeg", "application/json", "application/octet-stream")
        )
    }, IMPORT_FILES)


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != IMPORT_FILES || resultCode != RESULT_OK) return
        if (data?.data == null && (data?.clipData?.itemCount ?: 0) < 3) return

        val contents = mutableListOf<Uri>()
        data!!.data?.let { contents.add(it) } ?: data.clipData!!.run {
            for (i in 0 until itemCount) getItemAt(i).uri?.let { contents.add(it) }
        }

        val names = mutableListOf<String>()
        contents.forEach { uri ->
            contentResolver.query(uri, null, null, null, null)?.use {
                if (it.moveToFirst()) names.add(it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)))
            }
        }

        var filename = ""
        var basename = ""
        val atlas = ".atlas"
        if (names.count() >= 3) {
            for (name in names) {
                if (name.endsWith(atlas) && name.length > atlas.length) {
                    basename = name.dropLast(atlas.length)
                    break
                }
            }

            if (basename.isEmpty() || names.all { it != "$basename.png" }) {
                return
            }

            filename = if (names.contains("$basename.json")) "$basename.json"
            else if (names.contains("$basename.skel")) "$basename.skel"
            else return
        }

        val bg =
            names.filter { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") && it != "$basename.png" }
                .getOrNull(0)

        val files = mutableListOf<String>()
        if (filename.isNotEmpty()) {
            files.add(filename)
            files.add("$basename$atlas")
            files.add("$basename.png")
        }
        bg?.let { files.add(it) }

        getExternalFilesDir(null)?.listFiles()?.forEach {
            if (it.isFile && (bg != null || it.name != currentBackground)) it.delete()
        }

        files.map { contents[names.indexOf(it)] }.forEachIndexed { i, uri ->
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream("${getExternalFilesDir(null)}/${files[i]}").use { output ->
                    input.copyTo(output)
                }
            }
        }
        if (filename.isNotEmpty()) currentFile = filename
        bg?.let { currentBackground = it }
    }
}
