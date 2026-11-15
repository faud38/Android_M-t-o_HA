package fr.meteowidget

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File

/** Mise à jour automatique depuis les « Releases » du dépôt GitHub. */
object MiseAJour {
    const val ACTION = "fr.meteowidget.MISE_A_JOUR"
    private const val CANAL = "mises_a_jour"

    data class Version(val numero: Int, val url: String, val notes: String)

    fun derniere(): Version? {
        if (BuildConfig.DEPOT.isBlank()) return null
        return try {
            parApi()
        } catch (e: Exception) {
            // Secours : la page web « latest » redirige vers …/releases/tag/vN
            parPageWeb() ?: throw e
        }
    }

    private fun parApi(): Version? {
        val o = JSONObject(
            Reseau.texte(
                "https://api.github.com/repos/${BuildConfig.DEPOT}/releases/latest",
                mapOf("Accept" to "application/vnd.github+json"),
            ),
        )
        val numero = o.optString("tag_name").removePrefix("v").toIntOrNull() ?: return null
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) {
                return Version(numero, a.optString("browser_download_url"), o.optString("body"))
            }
        }
        return null
    }

    private fun parPageWeb(): Version? {
        val cx = java.net.URL("https://github.com/${BuildConfig.DEPOT}/releases/latest").openConnection() as java.net.HttpURLConnection
        cx.instanceFollowRedirects = false
        cx.connectTimeout = 20000
        cx.readTimeout = 30000
        cx.setRequestProperty("User-Agent", "meteo-widget")
        try {
            val lieu = cx.getHeaderField("Location") ?: return null
            val n = lieu.substringAfterLast("/tag/v", "").toIntOrNull() ?: return null
            return Version(n, "https://github.com/${BuildConfig.DEPOT}/releases/download/v$n/meteo-widget-v$n.apk", "")
        } finally {
            cx.disconnect()
        }
    }

    fun disponible(): Version? {
        val v = derniere()?.takeIf { it.numero > BuildConfig.VERSION_CODE } ?: return null
        val notes = try { nouveautes() } catch (_: Exception) { "" }
        return if (notes.isNotBlank()) v.copy(notes = notes) else v.copy(notes = nettoyer(v.notes))
    }

    /** Retire les lignes techniques (signatures de commit) des notes. */
    private fun nettoyer(t: String) = t.lines()
        .filterNot { it.startsWith("Co-Authored-By") || it.startsWith("Claude-Session") }
        .joinToString("\n").trim()

    /** Toutes les nouveautés depuis la version installée, la plus récente en premier. */
    private fun nouveautes(): String {
        val a = org.json.JSONArray(
            Reseau.texte(
                "https://api.github.com/repos/${BuildConfig.DEPOT}/releases?per_page=30",
                mapOf("Accept" to "application/vnd.github+json"),
            ),
        )
        val lignes = mutableListOf<Pair<Int, String>>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val n = o.optString("tag_name").removePrefix("v").toIntOrNull() ?: continue
            if (n <= BuildConfig.VERSION_CODE) continue
            val t = nettoyer(o.optString("body")).lines().firstOrNull { it.isNotBlank() } ?: continue
            lignes += n to "• v$n : $t"
        }
        return lignes.sortedByDescending { it.first }.joinToString("\n") { it.second }
    }

    fun verifierEtNotifier(c: Context) {
        Reglages.setDerVerifMaj(c, System.currentTimeMillis())
        val v = disponible() ?: return
        // Une seule notification par version
        val prefs = c.getSharedPreferences("reglages", Context.MODE_PRIVATE)
        if (prefs.getInt("maj_notifiee", 0) >= v.numero) return
        prefs.edit().putInt("maj_notifiee", v.numero).apply()
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CANAL, "Mises à jour", NotificationManager.IMPORTANCE_DEFAULT))
        val ouvrir = Intent(c, MainActivity::class.java).setAction(ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(c, 1, ouvrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = Notification.Builder(c, CANAL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Météo widget : version ${v.numero} disponible")
            .setContentText(v.notes.lines().firstOrNull()?.removePrefix("• ") ?: "Touchez pour installer la mise à jour")
            .setStyle(Notification.BigTextStyle().bigText(v.notes.ifBlank { "Touchez pour installer la mise à jour" } + "\n\nTouchez pour installer."))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(1, notif)
        } catch (_: SecurityException) {
        }
    }

    fun telecharger(c: Context, v: Version): File {
        val dossier = File(c.cacheDir, "apk").apply { mkdirs() }
        val f = File(dossier, "mise-a-jour.apk")
        Reseau.fichier(v.url, f)
        return f
    }

    /** Lance l'installation (Android demande confirmation). Renvoie false s'il faut d'abord autoriser l'installation. */
    fun installer(a: Activity, f: File): Boolean {
        if (!a.packageManager.canRequestPackageInstalls()) {
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
            return false
        }
        val uri = FileProvider.getUriForFile(a, "${a.packageName}.fichiers", f)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        a.startActivity(i)
        return true
    }
}
