package com.juscelino.sonivell.system

import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.util.concurrent.TimeUnit
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * O que no Android vem do sistema (seletor de arquivos, painel de saída de som, compartilhar),
 * feito com as ferramentas do Linux e do Java.
 */
object DesktopSystem {
    /** Janela principal, para os diálogos abrirem por cima dela. */
    @Volatile var window: Frame? = null

    private fun commandExists(name: String): Boolean =
        System.getenv("PATH").orEmpty().split(':').any { File(it, name).canExecute() }

    private fun launch(vararg cmd: String): Boolean = runCatching {
        ProcessBuilder(*cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        true
    }.getOrDefault(false)

    /** Abre o painel de som do sistema (saída de áudio): o do GNOME, do KDE, do XFCE ou o pavucontrol. */
    fun openSoundSettings(): Boolean {
        val desktop = System.getenv("XDG_CURRENT_DESKTOP").orEmpty().lowercase()
        val candidates = buildList {
            if ("kde" in desktop) add(arrayOf("systemsettings", "kcm_pulseaudio"))
            if ("gnome" in desktop || "unity" in desktop || "budgie" in desktop) add(arrayOf("gnome-control-center", "sound"))
            if ("cinnamon" in desktop) add(arrayOf("cinnamon-settings", "sound"))
            if ("mate" in desktop) add(arrayOf("mate-volume-control"))
            add(arrayOf("pavucontrol"))
            add(arrayOf("pwvucontrol"))
            add(arrayOf("gnome-control-center", "sound"))
            add(arrayOf("systemsettings", "kcm_pulseaudio"))
            add(arrayOf("xfce4-pulseaudio-plugin"))
        }
        return candidates.any { commandExists(it[0]) && launch(*it) }
    }

    /** Mostra o arquivo no gerenciador de arquivos (ou abre a pasta dele). */
    fun showInFolder(path: String): Boolean {
        val file = File(path)
        val uri = file.toURI().toString()
        if (commandExists("dbus-send") && launch(
                "dbus-send", "--session", "--dest=org.freedesktop.FileManager1", "--type=method_call",
                "/org/freedesktop/FileManager1", "org.freedesktop.FileManager1.ShowItems", "array:string:$uri", "string:",
            )
        ) return true
        return open(file.parentFile ?: file)
    }

    fun open(file: File): Boolean = commandExists("xdg-open") && launch("xdg-open", file.path)

    fun copyToClipboard(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }

    /** Move para a lixeira (gio/kioclient/trash-put); sem nenhum deles, apaga. Devolve se deu certo. */
    fun moveToTrash(file: File): Boolean {
        if (!file.exists()) return true
        val tried = listOf(arrayOf("gio", "trash", file.path), arrayOf("kioclient5", "move", file.path, "trash:/"), arrayOf("trash-put", file.path))
        for (cmd in tried) {
            if (!commandExists(cmd[0])) continue
            val ok = runCatching {
                val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
                p.inputStream.readBytes()
                p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0
            }.getOrDefault(false)
            if (ok && !file.exists()) return true
        }
        return runCatching { java.awt.Desktop.getDesktop().moveToTrash(file) }.getOrDefault(false) || file.delete()
    }

    private fun <T> onSwing(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return block()
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    /** Escolher um arquivo para abrir. [extensions] sem ponto ("m3u", "zip"); vazio aceita tudo. */
    fun chooseFile(title: String, extensions: List<String> = emptyList(), startDir: File? = null): File? = onSwing {
        val dialog = FileDialog(window, title, FileDialog.LOAD)
        startDir?.let { dialog.directory = it.path }
        if (extensions.isNotEmpty()) dialog.setFilenameFilter { _, name -> extensions.any { name.lowercase().endsWith(".$it") } }
        dialog.isVisible = true
        dialog.file?.let { File(dialog.directory, it) }
    }

    /** Escolher onde salvar, já com [suggestedName]. */
    fun chooseSaveFile(title: String, suggestedName: String, startDir: File? = null): File? = onSwing {
        val dialog = FileDialog(window, title, FileDialog.SAVE)
        dialog.directory = (startDir ?: File(System.getProperty("user.home"))).path
        dialog.file = suggestedName
        dialog.isVisible = true
        dialog.file?.let { File(dialog.directory, it) }
    }

    /** Escolher uma pasta (pastas de música, pasta de letras). */
    fun chooseFolder(title: String, startDir: File? = null): File? = onSwing {
        val chooser = JFileChooser(startDir ?: File(System.getProperty("user.home")))
        chooser.dialogTitle = title
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.isAcceptAllFileFilterUsed = false
        if (chooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    /** Escolher uma imagem (capa própria de um álbum). */
    fun chooseImage(title: String): File? = onSwing {
        val chooser = JFileChooser(File(System.getProperty("user.home"), "Imagens").takeIf { it.isDirectory } ?: File(System.getProperty("user.home")))
        chooser.dialogTitle = title
        chooser.fileFilter = FileNameExtensionFilter("Imagens", "jpg", "jpeg", "png", "webp", "gif", "bmp")
        if (chooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }
}
