package fr.meteowidget

import android.content.Context
import androidx.javascriptengine.JavaScriptSandbox
import java.util.concurrent.TimeUnit

/** Exécute le fichier de calculs partagé (meteo-calculs.js) avec le moteur JavaScript d'Android. */
object MoteurJs {
    fun disponible(): Boolean = try {
        JavaScriptSandbox.isSupported()
    } catch (_: Throwable) {
        false
    }

    /** À appeler hors du fil principal (tâche de fond). Renvoie la valeur de la dernière expression. */
    fun executer(c: Context, code: String): String {
        if (!disponible()) throw IllegalStateException("moteur JavaScript indisponible sur ce téléphone")
        val sandbox = JavaScriptSandbox.createConnectedInstanceAsync(c.applicationContext).get(30, TimeUnit.SECONDS)
        try {
            val isolate = sandbox.createIsolate()
            try {
                return isolate.evaluateJavaScriptAsync(code).get(60, TimeUnit.SECONDS)
            } finally {
                isolate.close()
            }
        } finally {
            sandbox.close()
        }
    }
}
