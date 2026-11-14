package fr.meteowidget

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Détail heure par heure d'un jour, ouvert en touchant une colonne du widget. */
class DetailActivity : Activity() {
    companion object {
        const val DATE = "date"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val dateTxt = intent.getStringExtra(DATE) ?: LocalDate.now().toString()
        val date = LocalDate.parse(dateTxt)
        val e = Donnees.charger(this)

        var heures = e.heures.filter { it.t.startsWith(dateTxt) }
        if (date == LocalDate.now()) {
            val h = LocalDateTime.now().hour
            heures = heures.filter { it.t.substring(11, 13).toInt() >= h }
        }
        val jour = e.jours.firstOrNull { it.date == date }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Dessin.FOND)
            setPadding(0, (16 * d).toInt(), 0, (16 * d).toInt())
        }
        col.addView(TextView(this).apply {
            text = "${Dessin.nomJourLong(date)} ${date.dayOfMonth} · heure par heure"
            textSize = 20f
            setTextColor(Dessin.TEXTE)
            setPadding((16 * d).toInt(), 0, (16 * d).toInt(), (4 * d).toInt())
        })
        if (jour != null) {
            col.addView(TextView(this).apply {
                val tmax = jour.tmax?.roundToInt()?.toString() ?: "–"
                val tmin = jour.tmin?.roundToInt()?.toString() ?: "–"
                text = "$tmax° / $tmin° · soleil ${Dessin.hm(jour.soleil)} · pluie ${Dessin.fr1(jour.pluie)} mm · fiabilité ${jour.note}/5"
                textSize = 13f
                setTextColor(Dessin.TEXTE2)
                setPadding((16 * d).toInt(), 0, (16 * d).toInt(), (12 * d).toInt())
            })
        }
        if (heures.isEmpty()) {
            col.addView(TextView(this).apply {
                text = "Pas de données horaires pour ce jour."
                setTextColor(Dessin.TEXTE2)
                setPadding((16 * d).toInt(), 0, 0, 0)
            })
        } else {
            val largeur = resources.displayMetrics.widthPixels
            col.addView(ImageView(this).apply {
                setImageBitmap(Dessin.detail(heures, largeur, d))
                adjustViewBounds = true
            })
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Dessin.FOND)
            addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }
}
