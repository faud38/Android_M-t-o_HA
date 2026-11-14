package fr.meteowidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Formats compacts du widget : Mini (2×1), Carré (2×2), Bandeau (4×1), Moyen (4×2),
 * partie météo de l'Horloge (4×2). Dessin en dp ; k = facteur d'échelle selon la taille réelle.
 */
object DessinFormats {
    private const val FOND = Dessin.FOND
    private const val TEXTE = Dessin.TEXTE
    private const val TEXTE2 = Dessin.TEXTE2
    private const val TRAIT = Dessin.TRAIT

    private fun toile(w: Int, h: Int, d: Float): Pair<Bitmap, Canvas> {
        val b = Bitmap.createBitmap(max(1, w), max(1, h), Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        cv.scale(d, d)
        return b to cv
    }

    private fun t(taille: Float, c: Int, gras: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
        Dessin.texte(taille, c, gras, align)

    private fun couper(txt: String, p: Paint, largeur: Float): String {
        var s = txt
        while (s.length > 3 && p.measureText(s) > largeur) s = s.dropLast(2).trimEnd(' ', '·', ',') + "…"
        return s
    }

    private fun aujourdhui(e: Etat): Jour? = e.jours.firstOrNull { it.date == LocalDate.now() } ?: e.jours.firstOrNull()
    private fun suivants(e: Etat, n: Int): List<Jour> = e.jours.filter { it.date.isAfter(LocalDate.now()) }.take(n)
    private fun temp(e: Etat): String = (e.tempHa ?: e.actuel?.temp)?.let { "${Dessin.fr1(it)}°" } ?: "–"
    private fun tMaxMin(j: Jour) = "${j.tmax?.roundToInt() ?: "–"}° / ${j.tmin?.roundToInt() ?: "–"}°"

    /** Icône d'un jour (avec tendance éventuelle), centrée sur cx. */
    private fun iconeJour(cv: Canvas, j: Jour, cx: Float, y: Float, h: Float) {
        if (j.tendance != null) {
            val l = Icones.largeurTendance(h)
            Icones.tendance(cv, j.tendance, j.elem, cx - l / 2, y, h, TEXTE)
        } else {
            Icones.dessiner(cv, j.icone, cx - h / 2, y, h, FOND)
        }
    }

    private fun points(cv: Canvas, x: Float, y: Float, n: Int, r: Float) {
        for (k in 1..5) cv.drawCircle(x + (k - 1) * r * 2.8f + r, y, r, Dessin.plein(if (k <= n) Dessin.couleurNote(n) else TRAIT))
    }

    private fun petitSoleil(cv: Canvas, x: Float, y: Float, taille: Float) = Icones.dessiner(cv, "soleil", x, y, taille, FOND)

    private fun moment(j: Jour): String {
        val horsJour = j.pluieJour != null && j.pluie >= 0.5 && j.pluieJour < 0.5
        if (!horsJour) return ""
        return listOfNotNull(if (j.pluieNuit >= 0.3) "nuit" else null, if (j.pluieSoir >= 0.3) "soir" else null).joinToString(" et ")
    }

    /** Ligne d'information : vigilance (si jaune ou plus) sinon pluie dans l'heure. Renvoie texte et couleur. */
    fun ligneInfo(e: Etat): Pair<String, Int>? {
        val v = e.vigilance
        if (v != null && v.niveau >= 1) {
            val ph = v.phenomenes.joinToString(", ") { it.first }
            return "⚠ Vigilance ${Dessin.NOMS_VIG[v.niveau.coerceIn(0, 3)]}${if (ph.isNotBlank()) " · $ph" else ""}" to
                Dessin.COULEURS_VIG[v.niveau.coerceIn(0, 3)]
        }
        val p = e.pluieHeure ?: return null
        return p.texte to (if (p.pluie) Icones.PLUIE else TEXTE2)
    }

    /* ---------------------------- Mini 2×1 ---------------------------- */
    fun mini(e: Etat, lieu: String, w: Int, h: Int, d: Float): Bitmap {
        val (b, cv) = toile(w, h, d)
        val W = w / d
        val H = h / d
        val k = min(W / 170f, H / 76f).coerceIn(0.6f, 1.8f)
        val (ic, cond) = Dessin.actuelIconeTexte(e)
        val ti = 46 * k
        Icones.dessiner(cv, ic, 8 * k, (H - ti) / 2, ti, FOND)
        val x = 8 * k + ti + 8 * k
        cv.drawText(temp(e), x, H / 2 + 5 * k, t(24 * k, TEXTE, true))
        val ps = t(10.5f * k, TEXTE2)
        cv.drawText(couper("${lieu.ifBlank { "Météo" }} · $cond", ps, W - x - 6 * k), x, H / 2 + 21 * k, ps)
        return b
    }

    /* ---------------------------- Carré 2×2 ---------------------------- */
    fun carre(e: Etat, w: Int, h: Int, d: Float): Bitmap {
        val (b, cv) = toile(w, h, d)
        val W = w / d
        val H = h / d
        val k = min(W / 170f, H / 170f).coerceIn(0.6f, 1.8f)
        val ox = (W - 170 * k) / 2
        val oy = (H - 170 * k) / 2
        fun X(v: Float) = ox + v * k
        fun Y(v: Float) = oy + v * k
        val (ic, cond) = Dessin.actuelIconeTexte(e)
        cv.drawText(temp(e), X(12f), Y(40f), t(28 * k, TEXTE, true))
        cv.drawText(cond, X(12f), Y(57f), t(11 * k, TEXTE2))
        Icones.dessiner(cv, ic, X(114f), Y(8f), 44 * k, FOND)
        val j = aujourdhui(e)
        if (j != null) {
            val p1 = t(12 * k, TEXTE)
            cv.drawText("Aujourd'hui ", X(12f), Y(83f), p1)
            cv.drawText(tMaxMin(j), X(12f) + p1.measureText("Aujourd'hui "), Y(83f), t(12 * k, TEXTE, true))
            // anneau d'ensoleillement
            val r = 15 * k
            val cx = X(30f)
            val cy = Y(112f)
            val ann = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3.5f * k; color = TRAIT }
            val rect = RectF(cx - r, cy - r, cx + r, cy + r)
            cv.drawArc(rect, 0f, 360f, false, ann)
            val f = (j.soleil / j.duree).coerceIn(0.0, 1.0).toFloat()
            if (f > 0) {
                ann.color = Icones.SOLEIL; ann.strokeCap = Paint.Cap.ROUND
                cv.drawArc(rect, -90f, 360f * f, false, ann)
            }
            cv.drawText(Dessin.hm(j.soleil), cx, cy + 3 * k, t(9 * k, TEXTE, false, Paint.Align.CENTER))
            // pluie du jour
            val aPluie = j.pluie >= 0.05
            val cp = if (aPluie) Icones.PLUIE else TEXTE2
            Dessin.goutte(cv, X(57f), Y(103f), 3.2f * k, cp)
            val pp = t(11.5f * k, cp)
            val tp = if (aPluie) "${Dessin.fr1(j.pluie)} mm" else "0 mm"
            cv.drawText(tp, X(64f), Y(107f), pp)
            val mo = moment(j)
            if (mo.isNotBlank()) cv.drawText(" $mo", X(64f) + pp.measureText(tp), Y(107f), t(11 * k, TEXTE2))
            val pf = t(11 * k, TEXTE2)
            cv.drawText("Fiabilité", X(54f), Y(125f), pf)
            points(cv, X(54f) + pf.measureText("Fiabilité ") , Y(121.5f), j.note, 2.2f * k)
        }
        ligneInfo(e)?.let { (txt, c) ->
            val pi = t(10 * k, c)
            if (!txt.startsWith("⚠")) Dessin.goutte(cv, X(15f), Y(152f), 2.8f * k, c)
            val x0 = if (txt.startsWith("⚠")) X(12f) else X(22f)
            cv.drawText(couper(txt, pi, X(162f) - x0), x0, Y(156f), pi)
        }
        return b
    }

    /* --------------------------- Bandeau 4×1 --------------------------- */
    /** Partie gauche = 34 % de la largeur (zone « actualiser »), puis 3 jours. */
    fun bandeau(e: Etat, lieu: String, w: Int, h: Int, d: Float): Bitmap {
        val (b, cv) = toile(w, h, d)
        val W = w / d
        val H = h / d
        val k = min(H / 76f, W / 330f).coerceIn(0.6f, 1.6f)
        val oy = (H - 76 * k) / 2
        val L = W * 0.34f
        val (ic, _) = Dessin.actuelIconeTexte(e)
        Icones.dessiner(cv, ic, 8 * k, oy + 16 * k, 44 * k, FOND)
        val x = 8 * k + 50 * k
        cv.drawText(temp(e), x, oy + 42 * k, t(22 * k, TEXTE, true))
        val pl = t(10 * k, TEXTE2)
        cv.drawText(couper(lieu.ifBlank { "Météo" }, pl, L - x - 4 * k), x, oy + 58 * k, pl)
        val js = suivants(e, 3)
        val Z = (W - L) / 3
        js.forEachIndexed { i, j ->
            val cx = L + Z * (i + 0.5f)
            cv.drawText("${Dessin.nomJour(j.date)} ${j.date.dayOfMonth}", cx, oy + 16 * k, t(10.5f * k, TEXTE2, false, Paint.Align.CENTER))
            iconeJour(cv, j, cx, oy + 20 * k, 26 * k)
            val pM = t(12 * k, TEXTE, true)
            val pm = t(11 * k, TEXTE2)
            val a = "${j.tmax?.roundToInt() ?: "–"}°"
            val bb = " ${j.tmin?.roundToInt() ?: "–"}°"
            val l = pM.measureText(a) + pm.measureText(bb)
            cv.drawText(a, cx - l / 2, oy + 66 * k, pM)
            cv.drawText(bb, cx - l / 2 + pM.measureText(a), oy + 66 * k, pm)
        }
        return b
    }

    /* ---------------------------- Moyen 4×2 ---------------------------- */
    /** Partie gauche = 32 % (temps actuel), puis 4 lignes de jours sur toute la hauteur. */
    fun moyen(e: Etat, lieu: String, w: Int, h: Int, d: Float): Bitmap {
        val (b, cv) = toile(w, h, d)
        val W = w / d
        val H = h / d
        val k = min(H / 170f, W / 330f).coerceIn(0.6f, 1.6f)
        val L = W * 0.32f
        val (ic, cond) = Dessin.actuelIconeTexte(e)
        Icones.dessiner(cv, ic, 10 * k, 10 * k, 46 * k, FOND)
        cv.drawText(temp(e), 12 * k, H - 58 * k, t(26 * k, TEXTE, true))
        val pc = t(11 * k, TEXTE2)
        cv.drawText(couper(cond, pc, L - 16 * k), 12 * k, H - 42 * k, pc)
        aujourdhui(e)?.let { j ->
            val p = t(11 * k, TEXTE)
            val tm = "${tMaxMin(j)} · "
            cv.drawText(tm, 12 * k, H - 26 * k, p)
            val x = 12 * k + p.measureText(tm)
            val cp = if (j.pluie >= 0.05) Icones.PLUIE else TEXTE2
            Dessin.goutte(cv, x + 3 * k, H - 30 * k, 3f * k, cp)
            cv.drawText(Dessin.fr1(j.pluie), x + 9 * k, H - 26 * k, t(11 * k, cp))
            val pl = t(10 * k, TEXTE2)
            val nom = couper(lieu.ifBlank { "Météo" }, pl, L - 60 * k) + " · "
            cv.drawText(nom, 12 * k, H - 10 * k, pl)
            points(cv, 12 * k + pl.measureText(nom), H - 13.5f * k, j.note, 2.2f * k)
        }
        val x0 = L
        val x1 = W - 10 * k
        val RW = x1 - x0
        val RH = H / 4
        suivants(e, 4).forEachIndexed { i, j ->
            val ry = RH * (i + 0.5f)
            if (i > 0) cv.drawLine(x0, RH * i, x1, RH * i, Paint().apply { color = 0xFF333333.toInt(); strokeWidth = 0.6f })
            cv.drawText(Dessin.nomJour(j.date), x0, ry + 4 * k, t(12 * k, TEXTE2))
            iconeJour(cv, j, x0 + RW * 0.23f, ry - 12 * k, 24 * k)
            petitSoleil(cv, x0 + RW * 0.36f, ry - 6.5f * k, 11 * k)
            cv.drawText(Dessin.hm(j.soleil), x0 + RW * 0.36f + 13 * k, ry + 4 * k, t(11.5f * k, Icones.SOLEIL))
            val aPluie = j.pluie >= 0.05
            cv.drawText(if (aPluie) "${Dessin.fr1(j.pluie)} mm" else "0 mm", x0 + RW * 0.58f, ry + 4 * k, t(11.5f * k, if (aPluie) Icones.PLUIE else TEXTE2))
            val pm = t(11.5f * k, TEXTE2, false, Paint.Align.RIGHT)
            val bb = " ${j.tmin?.roundToInt() ?: "–"}°"
            cv.drawText(bb, x1, ry + 4 * k, pm)
            cv.drawText("${j.tmax?.roundToInt() ?: "–"}°", x1 - pm.measureText(bb), ry + 4 * k, t(12.5f * k, TEXTE, true, Paint.Align.RIGHT))
        }
        return b
    }

    /* ------------------------ Horloge : partie météo ------------------------ */
    fun horloge(e: Etat, w: Int, h: Int, d: Float): Bitmap {
        val (b, cv) = toile(w, h, d)
        val W = w / d
        val H = h / d
        val k = min(W / 170f, H / 150f).coerceIn(0.6f, 1.8f)
        val xr = W - 10 * k
        val oy = (H - 104 * k) / 2
        val (ic, cond) = Dessin.actuelIconeTexte(e)
        val pt = t(26 * k, TEXTE, true, Paint.Align.RIGHT)
        val tt = temp(e)
        cv.drawText(tt, xr, oy + 34 * k, pt)
        Icones.dessiner(cv, ic, xr - pt.measureText(tt) - 46 * k, oy, 42 * k, FOND)
        val j = aujourdhui(e)
        val l2 = if (j != null) "$cond · ${tMaxMin(j)}" else cond
        cv.drawText(l2, xr, oy + 56 * k, t(11 * k, TEXTE2, false, Paint.Align.RIGHT))
        if (j != null) {
            val aPluie = j.pluie >= 0.05
            val pp = t(11.5f * k, if (aPluie) Icones.PLUIE else TEXTE2, false, Paint.Align.RIGHT)
            val tp = if (aPluie) "${Dessin.fr1(j.pluie)} mm" else "0 mm"
            cv.drawText(tp, xr, oy + 77 * k, pp)
            var x = xr - pp.measureText(tp)
            val sep = " · "
            val ps = t(11.5f * k, TEXTE2, false, Paint.Align.RIGHT)
            cv.drawText(sep, x, oy + 77 * k, ps)
            x -= ps.measureText(sep)
            val psol = t(11.5f * k, Icones.SOLEIL, false, Paint.Align.RIGHT)
            val ts = Dessin.hm(j.soleil)
            cv.drawText(ts, x, oy + 77 * k, psol)
            x -= psol.measureText(ts)
            petitSoleil(cv, x - 13 * k, oy + 67 * k, 11 * k)
            val r = 2.2f * k
            val lp = 5 * r * 2.8f
            points(cv, xr - lp, oy + 95 * k, j.note, r)
            cv.drawText("Fiabilité ", xr - lp, oy + 98.5f * k, t(10 * k, TEXTE2, false, Paint.Align.RIGHT))
        }
        return b
    }
}
