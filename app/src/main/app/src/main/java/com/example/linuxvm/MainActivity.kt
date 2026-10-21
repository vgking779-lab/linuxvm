package com.example.linuxvm

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Typeface
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private val distros = listOf(
        "Alpine 3.20" to "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.3-aarch64.tar.gz",
        "Ubuntu 24.04" to "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.1-base-arm64.tar.gz",
        "Outra (URL abaixo)" to ""
    )

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var log: TextView
    private lateinit var scroll: ScrollView
    private lateinit var spinner: Spinner
    private lateinit var urlField: EditText
    private lateinit var input: EditText
    private var shell: Process? = null

    private val rootfs get() = File(filesDir, "rootfs")
    private val nativeDir get() = applicationInfo.nativeLibraryDir

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }

        spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, distros.map { it.first })
        }
        urlField = EditText(this).apply { hint = "URL de rootfs .tar.gz (opcional)"; setSingleLine() }
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val install = Button(this).apply { text = "Instalar"; setOnClickListener { install() } }
        val start = Button(this).apply { text = "Iniciar"; setOnClickListener { start() } }
        val stop = Button(this).apply { text = "Parar"; setOnClickListener { shell?.destroy(); print("\n[parado]\n") } }
        buttons.addView(install); buttons.addView(start); buttons.addView(stop)

        log = TextView(this).apply { typeface = Typeface.MONOSPACE; textSize = 12f; setTextIsSelectable(true) }
        scroll = ScrollView(this).apply { addView(log) }
        input = EditText(this).apply {
            hint = "comando"; setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, _, _ -> send(); true }
        }

        root.addView(spinner); root.addView(urlField); root.addView(buttons)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(input)
        setContentView(root)
        print("ABI: ${android.os.Build.SUPPORTED_ABIS.joinToString()}\nToque em Instalar e depois em Iniciar.\n")
    }

    private fun print(s: String) = ui.post {
        log.append(s); scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun install() = Thread {
        try {
            val url = urlField.text.toString().trim().ifEmpty { distros[spinner.selectedItemPosition].second }
            if (url.isEmpty()) { print("Informe uma URL.\n"); return@Thread }
            print("Baixando $url\n")
            val tar = File(cacheDir, "rootfs.tar.gz")
            val c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = true
            c.inputStream.use { i -> tar.outputStream().use { o -> i.copyTo(o) } }
            print("Extraindo...\n")
            rootfs.deleteRecursively(); rootfs.mkdirs()
            val p = ProcessBuilder("/system/bin/tar", "-xzf", tar.absolutePath, "-C", rootfs.absolutePath)
                .redirectErrorStream(true).start()
            p.inputStream.bufferedReader().forEachLine { print("tar: $it\n") }
            p.waitFor()
            File(rootfs, "etc").mkdirs()
            File(rootfs, "etc/resolv.conf").apply { delete(); writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n") }
            tar.delete()
            print("Pronto. Toque em Iniciar.\n")
        } catch (e: Exception) { print("Erro: $e\n") }
    }.start()

    private fun start() {
        if (!File(rootfs, "bin").exists()) { print("Instale um rootfs primeiro.\n"); return }
        shell?.destroy()
        val tmp = File(cacheDir, "proot").apply { mkdirs() }
        val cmd = listOf(
            "$nativeDir/libproot.so", "--link2symlink", "-0", "-r", rootfs.absolutePath,
            "-b", "/dev", "-b", "/proc", "-b", "/sys", "-w", "/root",
            "/usr/bin/env", "-i", "HOME=/root", "TERM=xterm-256color",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "/bin/sh", "-i"
        )
        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        pb.environment().apply {
            put("PROOT_LOADER", "$nativeDir/libproot-loader.so")
            put("PROOT_TMP_DIR", tmp.absolutePath)
            put("LD_LIBRARY_PATH", nativeDir)
        }
        try {
            val p = pb.start(); shell = p
            print("[shell iniciado]\n")
            Thread { p.inputStream.bufferedReader().use { r ->
                val buf = CharArray(2048); var n: Int
                while (r.read(buf).also { n = it } > 0) print(String(buf, 0, n))
            }; print("\n[shell encerrado]\n") }.start()
        } catch (e: Exception) { print("Erro ao iniciar: $e\n") }
    }

    private fun send() {
        val s = input.text.toString(); input.setText("")
        print("# $s\n")
        Thread { try { shell?.outputStream?.apply { write((s + "\n").toByteArray()); flush() } } catch (_: Exception) {} }.start()
    }
}
