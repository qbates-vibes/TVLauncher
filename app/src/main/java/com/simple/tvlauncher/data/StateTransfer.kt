package com.simple.tvlauncher.data

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The setup as it was just before an import or restore replaced it. */
data class Snapshot(val folder: File, val label: String)

/**
 * Saves and loads whole setups through the app's own folder, which `adb` can reach (see [UserFiles]):
 *   files/state/current.json       this TV's setup, rewritten whenever it changes
 *   files/state/inbox/NAME/        a setup pushed from the Mac: state.json, plus media/ if it brings pictures and music
 *   files/state/snapshots/         the setup before each import or restore, newest [KEEP_SNAPSHOTS] kept
 *   files/state/rejected/          inbox setups that couldn't be read
 *   files/state/last-import.json   what the last import or restore did, for tvstate.sh to report
 * Call these off the main thread.
 */
class StateTransfer(context: Context, private val store: SettingsStore, private val repository: AppRepository) {
    private val context = context.applicationContext
    private val lock = Mutex()

    private class MediaKind(val name: String, val subfolder: String?, val extensions: Set<String>)

    private val mediaKinds = listOf(
        MediaKind("wallpaper", null, UserFiles.WALLPAPER_EXTENSIONS),
        MediaKind("logo", UserFiles.LOGO_FOLDER, UserFiles.IMAGE_EXTENSIONS),
        MediaKind("music", UserFiles.MUSIC_FOLDER, UserFiles.AUDIO_EXTENSIONS),
    )

    private fun stateFolder(name: String? = null): File? =
        context.getExternalFilesDir(null)?.let { File(it, if (name == null) STATE else "$STATE/$name") }?.apply { mkdirs() }

    fun writeCurrent(settings: LauncherSettings) {
        val folder = stateFolder() ?: return
        writeAtomically(File(folder, CURRENT), StateFile.write(settings, StateSource.thisTv(context)))
    }

    /** Applies the setups waiting in the inbox, oldest first, and returns a short message for each. */
    suspend fun importInbox(): List<String> = lock.withLock {
        val inbox = stateFolder(INBOX) ?: return emptyList()
        // tvstate.sh pushes into NAME.partial and renames it when the copy is complete.
        val waiting = inbox.listFiles { f -> f.isDirectory && !f.name.endsWith(".partial") }.orEmpty().sortedBy { it.lastModified() }
        waiting.map { folder ->
            try {
                apply(folder, "import-${folder.name}", moveMedia = true).also { folder.deleteRecursively() }
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                stateFolder(REJECTED)?.let { rejected ->
                    File(rejected, folder.name).deleteRecursively()
                    folder.renameTo(File(rejected, folder.name))
                }
                writeResult(JSONObject().put("status", "rejected").put("name", folder.name).put("reason", reason))
                "Couldn't import ${folder.name}: $reason"
            }
        }
    }

    /** Snapshots, newest first. */
    fun snapshots(): List<Snapshot> =
        stateFolder(SNAPSHOTS)?.listFiles { f -> File(f, STATE_FILE).isFile }.orEmpty()
            .sortedByDescending { it.name }
            .map { Snapshot(it, describe(it.name)) }

    /** Puts a snapshot's setup back. The current one is snapshotted first, so this can be undone too. */
    suspend fun restore(snapshot: Snapshot): String = lock.withLock {
        apply(snapshot.folder, "restore", moveMedia = false)
    }

    private suspend fun apply(folder: File, reason: String, moveMedia: Boolean): String {
        val stateFile = File(folder, STATE_FILE)
        require(stateFile.isFile) { "it has no $STATE_FILE" }
        require(stateFile.length() < MAX_STATE_BYTES) { "$STATE_FILE is too large" }
        val setup = StateFile.read(stateFile.readText())
        val media = File(folder, MEDIA).takeIf { it.isDirectory }

        takeSnapshot(reason, withMedia = media != null)
        val installed = repository.installedPackages()
        val (settings, swapped) = AppAliases.apply(setup.settings, installed)
        store.replaceAll(settings)
        media?.let { placeMedia(it, moveMedia) }
        pruneSnapshots()

        val missing = (settings.favorites + settings.appOrder).distinct().filter { it !in installed }
        val result = JSONObject()
            .put("status", "applied")
            .put("name", folder.name)
            .put("media", media != null)
            .put("swapped", JSONObject(swapped.toMap()))
            .put("notInstalled", JSONArray(missing))
        setup.source?.let { result.put("from", "${it.model}, Android ${it.android}, saved ${it.exportedAt}") }
        writeResult(result)

        val name = if (reason == "restore") "the earlier setup" else folder.name
        return if (missing.isEmpty()) "Loaded $name" else "Loaded $name. ${missing.size} of its apps aren't installed here."
    }

    private suspend fun takeSnapshot(reason: String, withMedia: Boolean) {
        val folder = File(stateFolder(SNAPSHOTS) ?: return, "${stamp().format(Date())}-$reason").apply { mkdirs() }
        writeAtomically(File(folder, STATE_FILE), StateFile.write(store.settings.first(), StateSource.thisTv(context)))
        if (!withMedia) return
        // The incoming setup replaces all media, so move the current files aside; renaming on the same storage is instant.
        for (kind in mediaKinds) {
            val target = File(folder, "$MEDIA/${kind.name}").apply { mkdirs() }
            UserFiles.list(context, kind.subfolder, kind.extensions).forEach { it.renameTo(File(target, it.name)) }
        }
    }

    /** Only plain files with a picture or music extension are taken, straight from each media folder. */
    private fun placeMedia(media: File, move: Boolean) {
        val root = context.getExternalFilesDir(null) ?: return
        for (kind in mediaKinds) {
            val destination = if (kind.subfolder == null) root else File(root, kind.subfolder).apply { mkdirs() }
            File(media, kind.name).listFiles { f -> f.isFile && f.extension.lowercase() in kind.extensions }.orEmpty().forEach {
                val target = File(destination, it.name)
                if (!move || !it.renameTo(target)) it.copyTo(target, overwrite = true)
            }
        }
    }

    private fun pruneSnapshots() {
        stateFolder(SNAPSHOTS)?.listFiles { f -> f.isDirectory }.orEmpty()
            .sortedByDescending { it.name }
            .drop(KEEP_SNAPSHOTS)
            .forEach { it.deleteRecursively() }
    }

    private fun writeResult(result: JSONObject) {
        val folder = stateFolder() ?: return
        writeAtomically(File(folder, LAST_IMPORT), result.put("at", stamp().format(Date())).toString(2))
    }

    private fun writeAtomically(file: File, text: String) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    /** "20261102-181500-123-import-living-room" → "Nov 2, 6:15 PM, before loading living-room". */
    private fun describe(folderName: String): String {
        val match = Regex("""(\d{8}-\d{6}-\d{3})-(import-(.*)|restore)""").matchEntire(folderName) ?: return folderName
        val time = stamp().parse(match.groupValues[1])?.let { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(it) }
        val what = match.groupValues[3].ifEmpty { null }?.let { "before loading $it" } ?: "before a restore"
        return listOfNotNull(time, what).joinToString(", ")
    }

    companion object {
        const val KEEP_SNAPSHOTS = 10

        private const val STATE = "state"
        private const val INBOX = "inbox"
        private const val SNAPSHOTS = "snapshots"
        private const val REJECTED = "rejected"
        private const val MEDIA = "media"
        private const val STATE_FILE = "state.json"
        private const val CURRENT = "current.json"
        private const val LAST_IMPORT = "last-import.json"
        private const val MAX_STATE_BYTES = 1_000_000
        private fun stamp() = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)
    }
}
