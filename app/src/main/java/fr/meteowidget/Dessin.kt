package fr.meteowidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object Dessin {
    const val TEXTE = 0xFFE1E1E1.toInt()
    const val TEXTE2 = 0xFF9B9B9B.toInt()
    const val TRAIT = 0xFF3A3A3A.toInt()
    const val FOND = 0xFF1C1C1C.toInt()
    const val FOND_AUJ = 0xFF262626.toInt()
    private const val VENT = 0xFF5DCAA5.toInt()
    val COULEURS_VIG = intArrayOf(0xFF4CAF50.toInt(), 0xFFFDD835.toInt(), 0xFFFB8C00.toInt(), 0xFFE53935.toInt())
    val NOMS_VIG = listOf("verte", "jaune", "orange", "rouge")
    val JOURS = listOf("lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim.")
    val JOURS_LONGS = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")
    private val DIRS = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    private val CONDITIONS_HA = mapOf(
        "clear-night" to "Nuit claire", "cloudy" to "Nuageux", "exceptional" to "Exceptionnel",
        "fog" to "Brouillard", "hail" to "Grêle", "lightning" to "Orage", "lightning-rainy" to "Orage",
        "partlycloudy" to "Éclaircies", "pouring" to "Averses", "rainy" to "Pluie", "snowy" to "Neige",
        "snowy-rainy" to "Neige et pluie", "sunny" to "Ensoleillé", "windy" to "Venteux", "windy-variant" to "Venteux",
    )

    private val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    internal fun texte(taillePx: Float, c: Int, gras: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = taillePx; color = c; textAlign = align
            typeface = if (gras) MEDIUM else Typeface.DEFAULT
        }

    internal fun plein(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; style = Paint.Style.FILL }

    fun fr1(v: Double): String = String.format(Locale.FRANCE, "%.1f", v)
    fun hm(sec: Double): String {
        val m = (sec / 60).roundToInt()
        return "${m / 60}h${(m % 60).toString().padStart(2, '0')}"
    }

    fun nomJour(d: LocalDate) = JOURS[d.dayOfWeek.value - 1]
    fun nomJourLong(d: LocalDate) = JOURS_LONGS[d.dayOfWeek.value - 1]

    /** Icône et libellé du temps actuel, depuis Home Assistant ou, à défaut, Open-Meteo. */
    internal fun actuelIconeTexte(e: Etat): Pair<String, String> {
        val nuit = e.actuel?.jour == false
        val s = e.etatHa
        if (s != null) {
            val ic = when (s) {
                "sunny" -> "soleil"
                "clear-night" -> "lune"
                "partlycloudy" -> if (nuit) "lune-nuage" else "eclaircies"
                "rainy", "pouring" -> "pluie"
                "lightning", "lightning-rainy" -> "orage"
                "fog" -> "brouillard"
                "snowy", "snowy-rainy", "hail" -> "neige"
                else -> "nuageux"
            }
            return ic to (CONDITIONS_HA[s] ?: s)
        }
        val c = e.actuel?.code ?: return "nuageux" to ""
        return when {
            c <= 1 -> (if (nuit) "lune" else "soleil") to (if (nuit) "Nuit claire" else "Ensoleillé")
            c == 2 -> (if (nuit) "lune-nuage" else "eclaircies") to "Éclaircies"
            c == 3 -> "nuageux" to "Nuageux"
            Calculs.estBrouillard(c) -> "brouillard" to "Brouillard"
            Calculs.estNeige(c) -> "neige" to "Neige"
            c >= 95 -> "orage" to "Orage"
            c >= 51 -> "pluie" to "Pluie"
            else -> "nuageux" to "Nuageux"
        }
    }

    internal fun goutte(cv: Canvas, x: Float, y: Float, r: Float, c: Int) {
        val p = plein(c)
        cv.drawCircle(x, y + 0.3f * r, r, p)
        val t = Path()
        t.moveTo(x - 0.92f * r, y + 0.05f * r); t.lineTo(x + 0.92f * r, y + 0.05f * r); t.lineTo(x, y - 1.6f * r); t.close()
        cv.drawPath(t, p)
    }

    internal fun petiteLune(cv: Canvas, x: Float, y: Float, r: Float, fond: Int) {
        cv.drawCircle(x, y, r, plein(TEXTE2))
        cv.drawCircle(x + 0.55f * r, y - 0.45f * r, 0.85f * r, plein(fond))
    }

    private fun flecheVent(cv: Canvas, x: Float, y: Float, taille: Float, deg: Double) {
        cv.save()
        cv.translate(x, y)
        cv.rotate(((deg + 180) % 360).toFloat())
        val s = taille / 14f
        val p = Path()
        p.moveTo(0f, -6f * s); p.lineTo(4f * s, 4f * s); p.lineTo(0f, 1.5f * s); p.lineTo(-4f * s, 4f * s); p.close()
        cv.drawPath(p, plein(VENT))
        cv.restore()
    }

    fun couleurNote(n: Int) = if (n >= 4) 0xFF8BC34A.toInt() else if (n == 3) 0xFFFFA726.toInt() else 0xFFEF5350.toInt()

    /* ------------------------------------------------------------------ */
    /* En-tête : lieu, temps actuel, vigilance, totaux, pluie dans l'heure */
    /* ------------------------------------------------------------------ */

    fun entete(e: Etat, lieu: String, w: Int, h: Int, d: Float): Bitmap {
        val bmp = Bitmap.createBitmap(max(1, w), max(1, h), Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val pad = 12 * d
        val nuit = e.actuel?.jour == false

        // Ligne 1 : lieu + vigilance
        val p1 = texte(11 * d, TEXTE2)
        val l1 = "${lieu.ifBlank { "Météo" }} · ${if (nuit) "cette nuit" else "maintenant"}"
        cv.drawText(l1, pad, pad + 10 * d, p1)
        var x = pad + p1.measureText(l1) + 8 * d
        e.vigilance?.let { v ->
            val coul = COULEURS_VIG[v.niveau.coerceIn(0, 3)]
            val pv = texte(10 * d, if (v.niveau == 0) TEXTE2 else TEXTE)
            var lib = if (v.niveau == 0) "Vigilance verte"
            else "Vigilance ${NOMS_VIG[v.niveau]} · ${v.phenomenes.joinToString(", ") { it.first }}"
            val dispo = w - pad - x - 22 * d
            while (lib.length > 10 && pv.measureText(lib) > dispo) lib = lib.dropLast(2).trimEnd(' ', ',', '·') + "…"
            if (dispo > 40 * d) {
                val larg = pv.measureText(lib) + 20 * d
                val fond = if (v.niveau == 0) 0xFF2A2A2A.toInt() else (coul and 0x00FFFFFF) or 0x55000000
                cv.drawRoundRect(RectF(x, pad - 2 * d, x + larg, pad + 14 * d), 8 * d, 8 * d, plein(fond))
                cv.drawCircle(x + 7 * d, pad + 6 * d, 3 * d, plein(coul))
                cv.drawText(lib, x + 13 * d, pad + 9.5f * d, pv)
            }
        }

        // Ligne 2 : temps actuel
        val (ic, cond) = actuelIconeTexte(e)
        Icones.dessiner(cv, ic, pad - 2 * d, 20 * d, 30 * d, FOND)
        val temp = e.tempHa ?: e.actuel?.temp
        val pt = texte(22 * d, TEXTE, true)
        val tTxt = if (temp != null) "${fr1(temp)} °C" else "–"
        val xt = pad + 34 * d
        cv.drawText(tTxt, xt, 44 * d, pt)
        val finTemp = xt + pt.measureText(tTxt) + 6 * d
        val pc = texte(11 * d, TEXTE2)
        cv.drawText(cond, finTemp, 44 * d, pc)
        val finGauche = finTemp + pc.measureText(cond) + 12 * d

        // Totaux de la semaine (à droite)
        if (e.jours.isNotEmpty()) {
            val pl = texte(10 * d, TEXTE2, false, Paint.Align.RIGHT)
            val pvv = texte(15 * d, TEXTE, true, Paint.Align.RIGHT)
            val vPluie = "${fr1(e.jours.sumOf { it.pluie })} mm"
            val vSoleil = hm(e.jours.sumOf { it.soleil }).replace("h", " h ")
            val xr1 = w - pad
            val larg1 = max(pl.measureText("Pluie 7 j"), pvv.measureText(vPluie))
            val xr2 = xr1 - larg1 - 14 * d
            val larg2 = max(pl.measureText("Soleil 7 j"), pvv.measureText(vSoleil))
            if (xr1 - larg1 > finGauche) {
                cv.drawText("Pluie 7 j", xr1, 28 * d, pl); cv.drawText(vPluie, xr1, 45 * d, pvv)
            }
            if (xr2 - larg2 > finGauche) {
                cv.drawText("Soleil 7 j", xr2, 28 * d, pl); cv.drawText(vSoleil, xr2, 45 * d, pvv)
            }
        }

        // Ligne 3 : pluie dans l'heure + heure de mise à jour
        val y3 = 64 * d
        val pm = texte(9 * d, TEXTE2, false, Paint.Align.RIGHT)
        val maj = if (e.derMaj > 0) "màj " + SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(e.derMaj)) else ""
        cv.drawText(maj, w - pad, y3, pm)
        // Pastille de qualité de l'air, à gauche de l'heure de mise à jour
        var largeurAir = 0f
        e.air?.let { a ->
            val (_, coul) = DessinFormats.niveauAir(a.indice)
            val ta = "Air ${a.indice.roundToInt()}"
            val xa = w - pad - pm.measureText(maj) - 10 * d
            cv.drawText(ta, xa, y3, pm)
            cv.drawCircle(xa - pm.measureText(ta) - 5 * d, y3 - 3 * d, 3 * d, plein(coul))
            largeurAir = pm.measureText(ta) + 18 * d
        }
        val dispoPluie = w - 2 * pad - pm.measureText(maj) - largeurAir - 10 * d
        if (!e.configure) {
            cv.drawText("Ouvrez l'application pour régler la position", pad, y3, texte(11 * d, TEXTE2))
        } else e.pluieHeure?.let { ph ->
            val c = if (ph.pluie) Icones.PLUIE else TEXTE2
            goutte(cv, pad + 4 * d, y3 - 4 * d, 3.2f * d, c)
            val pp = texte(11 * d, c, ph.pluie)
            var t = ph.texte
            while (t.length > 8 && pp.measureText(t) > dispoPluie - 70 * d) t = t.dropLast(2) + "…"
            cv.drawText(t, pad + 11 * d, y3, pp)
            // mini barre des 60 minutes
            val bx0 = pad + 11 * d + pp.measureText(t) + 10 * d
            val bx1 = min(bx0 + 90 * d, pad + dispoPluie)
            if (bx1 - bx0 > 30 * d) {
                val total = ph.segments.sumOf { it.first }.coerceAtLeast(1)
                var bx = bx0
                val cs = intArrayOf(TRAIT, 0xFF85B7EB.toInt(), Icones.PLUIE, 0xFF185FA5.toInt())
                ph.segments.forEach { (duree, niv) ->
                    val l = (bx1 - bx0) * duree / total
                    cv.drawRoundRect(RectF(bx, y3 - 7 * d, bx + l - 1.5f * d, y3 - 2 * d), 2 * d, 2 * d, plein(cs[niv.coerceIn(0, 3)]))
                    bx += l
                }
            }
        }
        return bmp
    }

    /* ------------------------------------------------------------------ */
    /* Colonnes des jours                                                   */
    /* ------------------------------------------------------------------ */

    fun colonnes(e: Etat, n: Int, w: Int, h: Int, d: Float): Bitmap {
        val bmp = Bitmap.createBitmap(max(1, w), max(1, h), Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val jours = e.jours.take(n)
        if (jours.isEmpty()) {
            val msg = when {
                !e.configure -> "Touchez l'icône « Météo widget » pour régler la position"
                e.erreur.isNotBlank() -> "Prévisions indisponibles pour le moment"
                else -> "Chargement des prévisions…"
            }
            cv.drawText(msg, w / 2f, h / 2f, texte(12 * d, TEXTE2, false, Paint.Align.CENTER))
            return bmp
        }
        val cw = w.toFloat() / jours.size
        val nominal = 226f
        val s = (h / d / nominal).coerceIn(0.55f, 1.35f)
        val haut = max(0f, (h - nominal * d * s) / 2)
        fun y(v: Float) = haut + v * d * s
        fun t(v: Float) = v * d * s

        jours.forEachIndexed { i, j ->
            val x0 = i * cw
            val cx = x0 + cw / 2
            val fond = if (i == 0) FOND_AUJ else FOND
            if (i == 0) cv.drawRoundRect(RectF(x0 + 2 * d, 2 * d, x0 + cw - 2 * d, h - 2 * d), 12 * d, 12 * d, plein(FOND_AUJ))

            // Nom du jour
            val pn = texte(t(13f), TEXTE, true)
            val pn2 = texte(t(12f), TEXTE2)
            val nom = nomJour(j.date) + " "
            val num = j.date.dayOfMonth.toString()
            val ln = pn.measureText(nom) + pn2.measureText(num)
            cv.drawText(nom, cx - ln / 2, y(13f), pn)
            cv.drawText(num, cx - ln / 2 + pn.measureText(nom), y(13f), pn2)

            // Icône (avec tendance si la journée évolue)
            val hi = t(34f)
            if (j.tendance != null) {
                val li = Icones.largeurTendance(hi)
                Icones.tendance(cv, j.tendance, j.elem, cx - li / 2, y(18f), hi, TEXTE)
            } else {
                Icones.dessiner(cv, j.icone, cx - hi / 2, y(18f), hi, fond)
            }

            // Températures
            val pmax = texte(t(14f), TEXTE, true)
            val pmin = texte(t(12f), TEXTE2)
            val tmax = j.tmax?.let { "${it.roundToInt()}°" } ?: "–"
            val tmin = j.tmin?.let { " ${it.roundToInt()}°" } ?: ""
            val lt = pmax.measureText(tmax) + pmin.measureText(tmin)
            cv.drawText(tmax, cx - lt / 2, y(68f), pmax)
            cv.drawText(tmin, cx - lt / 2 + pmax.measureText(tmax), y(68f), pmin)

            // Anneau d'ensoleillement
            val r = t(19f)
            val cy = y(96f)
            val ann = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = t(4f); color = TRAIT }
            val rect = RectF(cx - r, cy - r, cx + r, cy + r)
            cv.drawArc(rect, 0f, 360f, false, ann)
            val f = (j.soleil / j.duree).coerceIn(0.0, 1.0).toFloat()
            if (f > 0) {
                ann.color = Icones.SOLEIL; ann.strokeCap = Paint.Cap.ROUND
                cv.drawArc(rect, -90f, 360f * f, false, ann)
            }
            cv.drawText(hm(j.soleil), cx, cy + t(3.5f), texte(t(10f), TEXTE, false, Paint.Align.CENTER))

            // Pluie
            val aPluie = j.pluie >= 0.05
            val cp = if (aPluie) Icones.PLUIE else TEXTE2
            val pp = texte(t(11f), cp)
            val tp = if (aPluie) "${fr1(j.pluie)} mm" else "0 mm"
            val lp = t(9f) + pp.measureText(tp)
            goutte(cv, cx - lp / 2 + t(3f), y(130f), t(3f), cp)
            cv.drawText(tp, cx - lp / 2 + t(9f), y(134f), pp)

            // Moment de la pluie hors journée
            val horsJour = j.pluieJour != null && j.pluie >= 0.5 && j.pluieJour < 0.5
            val moments = listOfNotNull(if (j.pluieNuit >= 0.3) "nuit" else null, if (j.pluieSoir >= 0.3) "soir" else null)
            if (horsJour && moments.isNotEmpty()) {
                val pmo = texte(t(9f), TEXTE2)
                val tm = moments.joinToString(" et ")
                val lm = t(9f) + pmo.measureText(tm)
                petiteLune(cv, cx - lm / 2 + t(3f), y(144f), t(3.2f), fond)
                cv.drawText(tm, cx - lm / 2 + t(9f), y(147f), pmo)
            }

            // Nuages
            val pnu = texte(t(11f), TEXTE)
            val tn = "${j.nuages.roundToInt()} %"
            val lnu = t(15f) + pnu.measureText(tn)
            cv.save()
            cv.translate(cx - lnu / 2, y(152f))
            cv.scale(t(13f) / 48f * 1.1f, t(13f) / 48f * 1.1f)
            Icones.nuage(cv, 0xFFB4B2A9.toInt(), -6f, -12f)
            cv.restore()
            cv.drawText(tn, cx - lnu / 2 + t(15f), y(163f), pnu)

            // Vent
            j.vent?.let { v ->
                val pv = texte(t(11f), TEXTE)
                var tv = "${v.roundToInt()} km/h"
                if (j.dir != null && cw / d > 84) tv += " " + DIRS[((j.dir / 45).roundToInt()) % 8]
                val lv = t(14f) + pv.measureText(tv)
                if (j.dir != null) flecheVent(cv, cx - lv / 2 + t(5f), y(176f), t(13f), j.dir)
                cv.drawText(tv, cx - lv / 2 + t(14f), y(180f), pv)
            }

            // Fiabilité
            val lb = t(48f)
            cv.drawRoundRect(RectF(cx - lb / 2, y(187f), cx + lb / 2, y(223f)), t(6f), t(6f), plein(0x267F7F7F))
            cv.drawText("Fiabilité", cx, y(197f), texte(t(8f), TEXTE2, false, Paint.Align.CENTER))
            cv.drawText("${j.note}/5", cx, y(211f), texte(t(12f), couleurNote(j.note), true, Paint.Align.CENTER))
            for (k in 1..5) {
                val xx = cx + (k - 3) * t(5.5f)
                cv.drawCircle(xx, y(217.5f), t(2f), plein(if (k <= j.note) couleurNote(j.note) else TRAIT))
            }
        }
        return bmp
    }

    /* ------------------------------------------------------------------ */
    /* Détail heure par heure (écran ouvert en touchant un jour)            */
    /* ------------------------------------------------------------------ */

    fun detail(heures: List<Heure>, w: Int, d: Float): Bitmap {
        val ligne = 52 * d
        val h = max(1, (heures.size * ligne + 8 * d).toInt())
        val bmp = Bitmap.createBitmap(max(1, w), h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(FOND)
        val pad = 16 * d
        val colTemp = pad + 90 * d
        val colPluie = w * 0.47f
        val colNuages = w * 0.66f
        val colVent = w * 0.80f
        heures.forEachIndexed { i, hr ->
            val y0 = i * ligne
            val base = y0 + ligne / 2 + 5 * d
            if (i > 0) cv.drawLine(pad, y0, w - pad, y0, Paint().apply { color = TRAIT; strokeWidth = d })
            cv.drawText("${hr.t.substring(11, 13)} h", pad, base, texte(14 * d, TEXTE2))
            Icones.dessiner(cv, hr.ic, pad + 40 * d, y0 + ligne / 2 - 16 * d, 32 * d, FOND)
            cv.drawText(hr.temp?.let { "${it.roundToInt()}°" } ?: "–", colTemp, base, texte(16 * d, TEXTE, true))
            val aPluie = hr.pluie >= 0.05
            val cp = if (aPluie) Icones.PLUIE else TEXTE2
            goutte(cv, colPluie, base - 5 * d, 3.5f * d, cp)
            cv.drawText(if (aPluie) "${fr1(hr.pluie)} mm" else "0 mm", colPluie + 8 * d, base, texte(13 * d, cp))
            cv.drawText("${hr.nuages.roundToInt()} %", colNuages, base, texte(13 * d, TEXTE2))
            hr.dir?.let { flecheVent(cv, colVent + 6 * d, base - 5 * d, 14 * d, it) }
            hr.vent?.let { cv.drawText("${it.roundToInt()} km/h", colVent + 16 * d, base, texte(13 * d, TEXTE)) }
        }
        return bmp
    }
}
