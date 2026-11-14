package fr.meteowidget

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Reseau {
    private fun ouvrir(adresse: String, entetes: Map<String, String>): HttpURLConnection {
        val cx = URL(adresse).openConnection() as HttpURLConnection
        cx.connectTimeout = 20000
        cx.readTimeout = 30000
        cx.instanceFollowRedirects = true
        cx.setRequestProperty("User-Agent", "meteo-widget")
        entetes.forEach { (k, v) -> cx.setRequestProperty(k, v) }
        val code = cx.responseCode
        if (code !in 200..299) {
            cx.disconnect()
            throw IOException("HTTP $code ($adresse)")
        }
        return cx
    }

    fun texte(adresse: String, entetes: Map<String, String> = emptyMap()): String {
        val cx = ouvrir(adresse, entetes)
        try {
            return cx.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            cx.disconnect()
        }
    }

    fun fichier(adresse: String, cible: File) {
        val cx = ouvrir(adresse, emptyMap())
        try {
            cx.inputStream.use { entree -> cible.outputStream().use { entree.copyTo(it) } }
        } finally {
            cx.disconnect()
        }
    }
}
