package fr.meteowidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.work.WorkManager
import java.time.LocalDate
import kotlin.math.max

/** Formats compacts. Le grand format reste la classe MeteoWidget (pour garder les widgets déjà posés). */
enum class Format { MINI, CARRE, BANDEAU, MOYEN, HORLOGE }

abstract class WidgetBase(private val format: Format) : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        Planif.periodique(c)
        ids.forEach { RenduFormats.maj(c, m, it, format) }
        Planif.maintenant(c)
    }

    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, options: Bundle) {
        RenduFormats.maj(c, m, id, format)
    }

    override fun onEnabled(c: Context) = Planif.periodique(c)

    override fun onDisabled(c: Context) {
        if (!RenduFormats.ilResteDesWidgets(c)) WorkManager.getInstance(c).cancelUniqueWork(Planif.PERIODIQUE)
    }

    override fun onReceive(c: Context, intent: Intent) {
        super.onReceive(c, intent)
        if (intent.action == MeteoWidget.ACTUALISER) Planif.maintenant(c)
    }
}

class WidgetMini : WidgetBase(Format.MINI)
class WidgetCarre : WidgetBase(Format.CARRE)
class WidgetBandeau : WidgetBase(Format.BANDEAU)
class WidgetMoyen : WidgetBase(Format.MOYEN)
class WidgetHorloge : WidgetBase(Format.HORLOGE)

object RenduFormats {
    val CLASSES = mapOf(
        Format.MINI to WidgetMini::class.java,
        Format.CARRE to WidgetCarre::class.java,
        Format.BANDEAU to WidgetBandeau::class.java,
        Format.MOYEN to WidgetMoyen::class.java,
        Format.HORLOGE to WidgetHorloge::class.java,
    )

    fun majTous(c: Context) {
        val m = AppWidgetManager.getInstance(c)
        CLASSES.forEach { (f, cl) -> m.getAppWidgetIds(ComponentName(c, cl)).forEach { maj(c, m, it, f) } }
    }

    fun ilResteDesWidgets(c: Context): Boolean {
        val m = AppWidgetManager.getInstance(c)
        return (CLASSES.values + MeteoWidget::class.java).any { m.getAppWidgetIds(ComponentName(c, it)).isNotEmpty() }
    }

    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun detail(c: Context, code: Int, date: LocalDate?): PendingIntent {
        val i = Intent(c, DetailActivity::class.java)
            .putExtra(DetailActivity.DATE, (date ?: LocalDate.now()).toString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(c, code, i, FLAGS)
    }

    private fun actualiser(c: Context, f: Format, id: Int): PendingIntent {
        val i = Intent(c, CLASSES.getValue(f)).setAction(MeteoWidget.ACTUALISER)
        return PendingIntent.getBroadcast(c, 500000 + id, i, FLAGS)
    }

    fun maj(c: Context, m: AppWidgetManager, id: Int, f: Format) {
        val opts = m.getAppWidgetOptions(id)
        val d = c.resources.displayMetrics.density
        val (lDefaut, hDefaut) = when (f) {
            Format.MINI -> 150 to 70
            Format.CARRE -> 160 to 160
            Format.BANDEAU -> 320 to 70
            Format.MOYEN, Format.HORLOGE -> 320 to 160
        }
        val lDp = max(60, opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, lDefaut))
        val hDp = max(40, opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, hDefaut))
        val l = (lDp * d).toInt()
        val h = (hDp * d).toInt()
        val e = Donnees.charger(c)
        val lieu = Reglages.lieu(c)
        val code = id * 10
        val auj = LocalDate.now()

        val v: RemoteViews
        when (f) {
            Format.MINI, Format.CARRE -> {
                v = RemoteViews(c.packageName, R.layout.widget_petit)
                v.setImageViewBitmap(
                    R.id.image,
                    if (f == Format.MINI) DessinFormats.mini(e, lieu, l, h, d) else DessinFormats.carre(e, l, h, d),
                )
                v.setOnClickPendingIntent(R.id.racine, detail(c, code, auj))
            }
            Format.BANDEAU -> {
                v = RemoteViews(c.packageName, R.layout.widget_bandeau)
                v.setImageViewBitmap(R.id.image, DessinFormats.bandeau(e, lieu, l, h, d))
                v.setOnClickPendingIntent(R.id.zA, actualiser(c, f, id))
                val js = e.jours.filter { it.date.isAfter(auj) }.take(3)
                listOf(R.id.z1, R.id.z2, R.id.z3).forEachIndexed { i, z ->
                    v.setOnClickPendingIntent(z, detail(c, code + i + 1, js.getOrNull(i)?.date ?: auj.plusDays(i + 1L)))
                }
            }
            Format.MOYEN -> {
                v = RemoteViews(c.packageName, R.layout.widget_moyen)
                v.setImageViewBitmap(R.id.image, DessinFormats.moyen(e, lieu, l, h, d))
                v.setOnClickPendingIntent(R.id.zA, actualiser(c, f, id))
                val js = e.jours.filter { it.date.isAfter(auj) }.take(4)
                listOf(R.id.z1, R.id.z2, R.id.z3, R.id.z4).forEachIndexed { i, z ->
                    v.setOnClickPendingIntent(z, detail(c, code + i + 1, js.getOrNull(i)?.date ?: auj.plusDays(i + 1L)))
                }
            }
            Format.HORLOGE -> {
                v = RemoteViews(c.packageName, R.layout.widget_horloge)
                val lMeteo = (l * 0.52f).toInt()
                v.setImageViewBitmap(R.id.image, DessinFormats.horloge(e, lMeteo, h, d))
                val info = DessinFormats.ligneInfo(e)
                v.setTextViewText(R.id.info, info?.first ?: "")
                v.setTextColor(R.id.info, info?.second ?: Dessin.TEXTE2)
                v.setOnClickPendingIntent(R.id.image, detail(c, code, auj))
                v.setOnClickPendingIntent(R.id.info, detail(c, code + 1, auj))
                // L'horloge ouvre l'application Horloge du téléphone si elle existe
                val horloge = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                v.setOnClickPendingIntent(
                    R.id.heure,
                    PendingIntent.getActivity(c, code + 2, horloge, FLAGS),
                )
            }
        }
        m.updateAppWidget(id, v)
    }
}
