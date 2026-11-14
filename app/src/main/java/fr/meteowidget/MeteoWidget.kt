package fr.meteowidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class MeteoWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        Planif.periodique(c)
        ids.forEach { Rendu.maj(c, m, it) }
        Planif.maintenant(c)
    }

    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, options: Bundle) {
        Rendu.maj(c, m, id)
    }

    override fun onEnabled(c: Context) {
        Planif.periodique(c)
    }

    override fun onDisabled(c: Context) {
        WorkManager.getInstance(c).cancelUniqueWork(Planif.PERIODIQUE)
    }

    override fun onReceive(c: Context, intent: Intent) {
        super.onReceive(c, intent)
        if (intent.action == ACTUALISER) Planif.maintenant(c)
    }

    companion object {
        const val ACTUALISER = "fr.meteowidget.ACTUALISER"
    }
}

object Planif {
    const val PERIODIQUE = "meteo-periodique"
    private const val IMMEDIAT = "meteo-immediat"

    private fun reseau() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun periodique(c: Context) {
        val req = PeriodicWorkRequestBuilder<MajWorker>(30, TimeUnit.MINUTES).setConstraints(reseau()).build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(PERIODIQUE, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun maintenant(c: Context) {
        val req = OneTimeWorkRequestBuilder<MajWorker>().setConstraints(reseau()).build()
        WorkManager.getInstance(c).enqueueUniqueWork(IMMEDIAT, ExistingWorkPolicy.REPLACE, req)
    }
}

/** Tâche de fond : télécharge les prévisions, redessine les widgets, vérifie les mises à jour. */
class MajWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result {
        val c = applicationContext
        try {
            Donnees.actualiser(c)
        } catch (e: Exception) {
            Reglages.setErreur(c, e.message ?: e.toString())
        }
        Rendu.majTous(c)
        try {
            if (System.currentTimeMillis() - Reglages.derVerifMaj(c) > 12 * 3600 * 1000L) {
                MiseAJour.verifierEtNotifier(c)
            }
        } catch (_: Exception) {
        }
        return Result.success()
    }
}

object Rendu {
    private const val ENTETE_DP = 72
    private val ZONES = intArrayOf(R.id.j0, R.id.j1, R.id.j2, R.id.j3, R.id.j4, R.id.j5, R.id.j6)

    fun majTous(c: Context) {
        val m = AppWidgetManager.getInstance(c)
        m.getAppWidgetIds(ComponentName(c, MeteoWidget::class.java)).forEach { maj(c, m, it) }
    }

    fun maj(c: Context, m: AppWidgetManager, id: Int) {
        val opts = m.getAppWidgetOptions(id)
        val d = c.resources.displayMetrics.density
        // Taille réelle du widget (en dp), en mode portrait
        val lDp = max(150, opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300))
        val hDp = max(140, opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 230))
        val e = Donnees.charger(c)
        val n = min(min(7, max(3, lDp / 62)), max(1, e.jours.size))

        val l = (lDp * d).toInt()
        val hEnt = (ENTETE_DP * d).toInt()
        val hCol = max((60 * d).toInt(), ((hDp - ENTETE_DP) * d).toInt())

        val v = RemoteViews(c.packageName, R.layout.widget)
        v.setImageViewBitmap(R.id.entete, Dessin.entete(e, Reglages.lieu(c), l, hEnt, d))
        v.setImageViewBitmap(R.id.colonnes, Dessin.colonnes(e, n, l, hCol, d))

        // En-tête : touche = actualiser
        val actu = Intent(c, MeteoWidget::class.java).setAction(MeteoWidget.ACTUALISER)
        v.setOnClickPendingIntent(
            R.id.entete,
            PendingIntent.getBroadcast(c, id, actu, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        )

        // Colonnes : touche = détail heure par heure du jour
        val jours = e.jours.take(n)
        ZONES.forEachIndexed { i, z ->
            if (i < jours.size) {
                v.setViewVisibility(z, View.VISIBLE)
                val det = Intent(c, DetailActivity::class.java)
                    .putExtra(DetailActivity.DATE, jours[i].date.toString())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                v.setOnClickPendingIntent(
                    z,
                    PendingIntent.getActivity(c, id * 10 + i, det, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
                )
            } else {
                v.setViewVisibility(z, View.GONE)
            }
        }
        m.updateAppWidget(id, v)
    }
}
