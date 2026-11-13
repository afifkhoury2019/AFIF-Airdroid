package uk.afif.link

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Token-protected read-only HTTP server over the app's external files directory.
 * This is what the dashboard "Access Window" points at on the LAN.
 *
 *   GET /                      -> HTML index
 *   GET /api/files?path=rel    -> JSON listing
 *   GET /api/download?path=rel -> raw bytes
 */
class FileServer(private val context: android.content.Context, private val port: Int) {

    @Volatile private var running = false
    private var server: ServerSocket? = null
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread {
            try {
                val s = ServerSocket(port)
                server = s
                while (running) {
                    val client = try { s.accept() } catch (e: Exception) { break }
                    Thread { handle(client) }.also { it.isDaemon = true }.start()
                }
            } catch (e: Exception) {
                // Port in use or blocked; surfaced in the activity log.
            } finally {
                try { server?.close() } catch (_: Exception) {}
            }
        }.also { it.isDaemon = true }
        thread?.start()
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) {}
        server = null
        thread = null
    }

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = 8000
            val input = BufferedInputStream(socket.getInputStream())
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val rawPath = parts[1]
            val query = rawPath.substringAfter("?", "")
            val path = rawPath.substringBefore("?").trimEnd('/').ifBlank { "/" }
            val out = socket.getOutputStream()

            val supplied = queryParam(query, "token")
            val expected = Prefs.token(context)
            if (expected.isNotBlank() && supplied != expected) {
                respond(out, 401, "application/json", """{"ok":false,"error":"unauthorised"}""")
                return
            }

            when {
                path == "/" -> respond(out, 200, "text/html; charset=utf-8", indexHtml())
                path == "/api/files" -> respond(
                    out, 200, "application/json", listing(queryParam(query, "path") ?: "").toString()
                )
                path == "/api/download" -> serveFile(out, queryParam(query, "path") ?: "")
                else -> respond(out, 404, "application/json", """{"ok":false,"error":"not found"}""")
            }
        } catch (_: Exception) {
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun serveFile(out: OutputStream, rel: String) {
        val root = CommandRouter.rootDir(context).canonicalFile
        val f = File(root, rel).canonicalFile
        if (!f.path.startsWith(root.path) || !f.isFile) {
            respond(out, 404, "application/json", """{"ok":false,"error":"not found"}""")
            return
        }
        val header = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/octet-stream\r\n" +
            "Content-Length: ${f.length()}\r\n" +
            "Content-Disposition: attachment; filename=\"${f.name.replace("\"", "")}\"\r\n" +
            "Connection: close\r\n\r\n"
        out.write(header.toByteArray())
        FileInputStream(f).use { fi ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = fi.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
            }
        }
        out.flush()
    }

    private fun listing(rel: String): JSONObject {
        val root = CommandRouter.rootDir(context).canonicalFile
        val dir = File(root, rel).canonicalFile
        if (!dir.path.startsWith(root.path) || !dir.isDirectory) {
            return JSONObject().put("ok", false).put("error", "not a directory")
        }
        val arr = JSONArray()
        dir.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { f ->
            arr.put(
                JSONObject()
                    .put("name", f.name)
                    .put("dir", f.isDirectory)
                    .put("size", if (f.isDirectory) 0 else f.length())
                    .put("sizeHuman", if (f.isDirectory) "-" else DeviceState.human(f.length()))
                    .put("modified", f.lastModified())
                    .put("rel", File(rel, f.name).path.replace('\\', '/'))
            )
        }
        return JSONObject().put("ok", true).put("path", rel).put("entries", arr)
    }

    private fun indexHtml(): String = """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>AFIF Link</title>
        <style>
          body{background:#0a1428;color:#c8d8ff;font-family:Segoe UI,Arial,sans-serif;margin:0;padding:20px}
          h1{color:#00d4ff;font-size:1.3rem;margin:0 0 4px}
          p{color:#7f9dd0;font-size:.85rem;margin:0 0 18px}
          a{color:#7fe3ff;text-decoration:none} a:hover{text-decoration:underline}
          .card{background:#0f1c3a;border:1px solid #1e3a6e;border-radius:10px;padding:14px 16px}
          code{background:#081530;padding:2px 6px;border-radius:4px;color:#8fd4ff}
        </style></head><body>
        <h1>AFIF Link</h1>
        <p>Read-only file bridge for the Node-RED dashboard.</p>
        <div class="card">
          <p><a href="/api/files?path=">Browse files</a></p>
          <p>Device: <code>${Prefs.deviceId(context)}</code></p>
          <p>Root: <code>${CommandRouter.rootDir(context).absolutePath}</code></p>
        </div>
        </body></html>
    """.trimIndent()

    private fun respond(out: OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray()
        val header = "HTTP/1.1 $code OK\r\nContent-Type: $type\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun readLine(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
            if (sb.length > 4096) break
        }
        return sb.toString()
    }

    private fun queryParam(query: String, name: String): String? {
        if (query.isBlank()) return null
        for (pair in query.split("&")) {
            val idx = pair.indexOf('=')
            val k = if (idx >= 0) pair.substring(0, idx) else pair
            if (k == name) {
                val v = if (idx >= 0) pair.substring(idx + 1) else ""
                return URLDecoder.decode(v, "UTF-8")
            }
        }
        return null
    }
}
