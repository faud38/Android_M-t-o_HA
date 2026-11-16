package fr.meteowidget

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * Semaine + détail heure par heure.
 * Les pages des jours suivent le doigt (glisser à gauche / à droite) ; la barre du haut suit la page.
 */
class DetailActivity : Activity() {
    companion object {
        const val DATE = "date"
    }

    private var d = 1f
    private lateinit var e: Etat
    private lateinit var jours: List<Jour>
    private lateinit var barreDefile: HorizontalScrollView
    private lateinit var pages: ViewPager2
    private val boutons = mutableListOf<LinearLayout>()
    private val images = LruCache<String, Bitmap>(4)

    private fun px(v: Float) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        d = resources.displayMetrics.density
        e = Donnees.charger(this)
        val demande = intent.getStringExtra(DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        jours = e.jours.filter { !it.date.isBefore(LocalDate.now()) }.take(7)

        val racine = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Dessin.FOND)
            setPadding(0, px(12f), 0, 0)
        }

        racine.addView(TextView(this).apply {
            text = "⟳  Actualiser"
            textSize = 13f
            setTextColor(Dessin.TEXTE2)
            gravity = Gravity.END
            setPadding(px(16f), 0, px(16f), px(6f))
            setOnClickListener {
                Planif.maintenant(this@DetailActivity)
                Toast.makeText(this@DetailActivity, "Actualisation demandée", Toast.LENGTH_SHORT).show()
            }
        })

        // Bandeau de vigilance (jaune ou plus), comme sur la carte Home Assistant
        e.vigilance?.takeIf { it.niveau >= 1 }?.let { v ->
            val n = v.niveau.coerceIn(1, 3)
            val coul = Dessin.COULEURS_VIG[n]
            val ph = v.phenomenes.joinToString(", ") { it.first }
            racine.addView(TextView(this).apply {
                text = "⚠  Vigilance ${Dessin.NOMS_VIG[n]}" + if (ph.isNotBlank()) "\n$ph" else ""
                textSize = 14f
                setTextColor(coul)
                setPadding(px(14f), px(10f), px(14f), px(10f))
                background = GradientDrawable().apply {
                    setColor((coul and 0x00FFFFFF) or 0x26000000)
                    cornerRadius = 10 * d
                    setStroke(px(1f), coul)
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(px(16f), 0, px(16f), px(10f))
            })
        }

        // Barre des jours
        val barre = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(px(8f), 0, px(8f), px(8f)) }
        val lb = maxOf(px(52f), (resources.displayMetrics.widthPixels - px(16f)) / maxOf(1, jours.size))
        jours.forEachIndexed { i, j ->
            val b = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, px(6f), 0, px(6f))
                isClickable = true
                setOnClickListener { pages.setCurrentItem(i, true) }
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
            boutons += b
        }
        barreDefile = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(barre)
        }
        racine.addView(barreDefile)

        // Pages des jours
        pages = ViewPager2(this).apply {
            adapter = Pages()
            offscreenPageLimit = 1
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) = surligner(position)
            })
        }
        racine.addView(pages, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(racine)

        val debut = jours.indexOfFirst { it.date == demande }.coerceAtLeast(0)
        pages.setCurrentItem(debut, false)
        surligner(debut)
    }

    private fun surligner(i: Int) {
        boutons.forEachIndexed { k, b ->
            b.background = if (k == i) GradientDrawable().apply { setColor(Dessin.FOND_AUJ); cornerRadius = 10 * d; setStroke(px(1f), Dessin.TRAIT) } else null
        }
        boutons.getOrNull(i)?.let { b ->
            barreDefile.post { barreDefile.smoothScrollTo(maxOf(0, b.left - (barreDefile.width - b.width) / 2), 0) }
        }
    }

    private fun heuresDu(date: LocalDate): List<Heure> {
        val txt = date.toString()
        var h = e.heures.filter { it.t.startsWith(txt) }
        if (date == LocalDate.now()) {
            val maintenant = LocalDateTime.now().hour
            h = h.filter { it.t.substring(11, 13).toInt() >= maintenant }
        }
        return h
    }

    private fun imageDu(date: LocalDate): Bitmap? {
        val cle = date.toString()
        images.get(cle)?.let { return it }
        val h = heuresDu(date)
        if (h.isEmpty()) return null
        return Dessin.detail(h, resources.displayMetrics.widthPixels, d).also { images.put(cle, it) }
    }

    private inner class Page(val defile: ScrollView, val titre: TextView, val resume: TextView, val vide: TextView, val image: ImageView) :
        RecyclerView.ViewHolder(defile)

    private inner class Pages : RecyclerView.Adapter<Page>() {
        override fun getItemCount() = jours.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Page {
            val col = LinearLayout(this@DetailActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, px(8f), 0, px(16f)) }
            val titre = TextView(this@DetailActivity).apply {
                textSize = 20f; setTextColor(Dessin.TEXTE); setPadding(px(16f), 0, px(16f), px(4f))
            }
            val resume = TextView(this@DetailActivity).apply {
                textSize = 13f; setTextColor(Dessin.TEXTE2); setPadding(px(16f), 0, px(16f), px(12f))
            }
            val vide = TextView(this@DetailActivity).apply {
                text = "Pas de données horaires pour ce jour."; setTextColor(Dessin.TEXTE2); setPadding(px(16f), 0, 0, 0)
            }
            val image = ImageView(this@DetailActivity).apply { adjustViewBounds = true }
            col.addView(titre); col.addView(resume); col.addView(vide); col.addView(image)
            val defile = ScrollView(this@DetailActivity).apply {
                setBackgroundColor(Dessin.FOND)
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            return Page(defile, titre, resume, vide, image)
        }

        override fun onBindViewHolder(p: Page, position: Int) {
            val j = jours[position]
            p.titre.text = "${Dessin.nomJourLong(j.date)} ${j.date.dayOfMonth} · heure par heure"
            p.resume.text = "${j.tmax?.roundToInt() ?: "–"}° / ${j.tmin?.roundToInt() ?: "–"}° · soleil ${Dessin.hm(j.soleil)} · " +
                "pluie ${Dessin.fr1(j.pluie)} mm · fiabilité ${j.note}/5"
            val img = imageDu(j.date)
            p.vide.visibility = if (img == null) View.VISIBLE else View.GONE
            p.image.setImageBitmap(img)
            p.defile.scrollTo(0, 0)
        }
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
}
