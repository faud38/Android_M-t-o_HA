package fr.meteowidget

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Semaine + détail heure par heure. Ouvert sur le jour touché dans le widget ; on change de jour d'un toucher. */
class DetailActivity : Activity() {
    companion object {
        const val DATE = "date"
    }

    private var d = 1f
    private lateinit var e: Etat
    private lateinit var barre: LinearLayout
    private lateinit var titre: TextView
    private lateinit var resume: TextView
    private lateinit var image: ImageView
    private lateinit var vide: TextView
    private lateinit var defile: ScrollView
    private val boutons = mutableListOf<Pair<LocalDate, LinearLayout>>()

    private fun px(v: Float) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        d = resources.displayMetrics.density
        e = Donnees.charger(this)
        val demande = intent.getStringExtra(DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        val jours = e.jours.filter { !it.date.isBefore(LocalDate.now()) }.take(7)

        val racine = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Dessin.FOND)
            setPadding(0, px(12f), 0, 0)
        }

        // Barre des jours
        barre = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(px(8f), 0, px(8f), px(8f)) }
        val largeurEcran = resources.displayMetrics.widthPixels
        val lb = maxOf(px(52f), (largeurEcran - px(16f)) / maxOf(1, jours.size))
        jours.forEach { j ->
            val b = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, px(6f), 0, px(6f))
                isClickable = true
                setOnClickListener { choisir(j.date) }
            }
            b.addView(TextView(this).apply {
                text = if (j.date == LocalDate.now()) "Auj." else Dessin.nomJour(j.date)
                textSize = 12f; setTextColor(Dessin.TEXTE2); gravity = Gravity.CENTER
            })
            b.addView(TextView(this).apply {
                text = "${j.date.dayOfMonth}"
                textSize = 11f; setTextColor(Dessin.TEXTE2); gravity = Gravity.CENTER
            })
            b.addView(ImageView(this).apply { setImageBitmap(icone(j, px(34f))) }, LinearLayout.LayoutParams(px(40f), px(34f)).apply { topMargin = px(2f) })
            b.addView(TextView(this).apply {
                text = "${j.tmax?.roundToInt() ?: "–"}°"
                textSize = 13f; setTextColor(Dessin.TEXTE); gravity = Gravity.CENTER
            })
            b.addView(TextView(this).apply {
                text = "${j.tmin?.roundToInt() ?: "–"}°"
                textSize = 11f; setTextColor(Dessin.TEXTE2); gravity = Gravity.CENTER
            })
            b.addView(ImageView(this).apply { setImageBitmap(points(j.note)) }, LinearLayout.LayoutParams(px(34f), px(8f)).apply { topMargin = px(4f) })
            barre.addView(b, LinearLayout.LayoutParams(lb, ViewGroup.LayoutParams.WRAP_CONTENT))
            boutons += j.date to b
        }
        racine.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(barre)
        })

        // Détail du jour choisi
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, px(8f), 0, px(16f)) }
        titre = TextView(this).apply {
            textSize = 20f; setTextColor(Dessin.TEXTE); setPadding(px(16f), 0, px(16f), px(4f))
        }
        resume = TextView(this).apply {
            textSize = 13f; setTextColor(Dessin.TEXTE2); setPadding(px(16f), 0, px(16f), px(12f))
        }
        vide = TextView(this).apply {
            text = "Pas de données horaires pour ce jour."; setTextColor(Dessin.TEXTE2); setPadding(px(16f), 0, 0, 0)
        }
        image = ImageView(this).apply { adjustViewBounds = true }
        col.addView(titre); col.addView(resume); col.addView(vide); col.addView(image)
        defile = ScrollView(this).apply {
            setBackgroundColor(Dessin.FOND)
            addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        racine.addView(defile, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(racine)

        choisir(if (jours.any { it.date == demande }) demande else jours.firstOrNull()?.date ?: demande)
    }

    /** Indice de fiabilité : 5 points, colorés selon la note. */
    private fun points(n: Int): Bitmap {
        val w = px(34f); val h = px(8f)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val r = 2.6f * d
        val pas = (w - 2 * r) / 4
        for (k in 0 until 5) {
            cv.drawCircle(r + k * pas, h / 2f, r, Dessin.plein(if (k < n) Dessin.couleurNote(n) else Dessin.TRAIT))
        }
        return bmp
    }

    private fun icone(j: Jour, h: Int): Bitmap {
        val w = (h * 40f / 34f).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        if (j.tendance != null) {
            val l = Icones.largeurTendance(h.toFloat())
            val s = minOf(1f, w / l)
            Icones.tendance(cv, j.tendance, j.elem, (w - l * s) / 2, (h - h * s) / 2, h * s, Dessin.TEXTE)
        } else {
            Icones.dessiner(cv, j.icone, (w - h) / 2f, 0f, h.toFloat(), Dessin.FOND)
        }
        return bmp
    }

    private fun choisir(date: LocalDate) {
        boutons.forEach { (dt, b) ->
            b.background = if (dt == date) GradientDrawable().apply { setColor(Dessin.FOND_AUJ); cornerRadius = 10 * d; setStroke(px(1f), Dessin.TRAIT) } else null
        }
        val dateTxt = date.toString()
        var heures = e.heures.filter { it.t.startsWith(dateTxt) }
        if (date == LocalDate.now()) {
            val h = LocalDateTime.now().hour
            heures = heures.filter { it.t.substring(11, 13).toInt() >= h }
        }
        titre.text = "${Dessin.nomJourLong(date)} ${date.dayOfMonth} · heure par heure"
        val jour = e.jours.firstOrNull { it.date == date }
        resume.text = if (jour != null) {
            "${jour.tmax?.roundToInt() ?: "–"}° / ${jour.tmin?.roundToInt() ?: "–"}° · soleil ${Dessin.hm(jour.soleil)} · " +
                "pluie ${Dessin.fr1(jour.pluie)} mm · fiabilité ${jour.note}/5"
        } else ""
        if (heures.isEmpty()) {
            vide.visibility = android.view.View.VISIBLE
            image.setImageDrawable(null)
        } else {
            vide.visibility = android.view.View.GONE
            image.setImageBitmap(Dessin.detail(heures, resources.displayMetrics.widthPixels, d))
        }
        defile.scrollTo(0, 0)
    }
}
