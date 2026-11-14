package fr.meteowidget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

/** Icônes météo, dessinées dans une boîte de 48 × 50 unités (mêmes formes que la carte). */
object Icones {
    const val SOLEIL = 0xFFEF9F27.toInt()
    const val PLUIE = 0xFF378ADD.toInt()
    private const val GOUTTE = 0xFF85B7EB.toInt()
    private const val NEIGE = 0xFFB5D4F4.toInt()
    private const val GRIS_CLAIR = 0xFFB4B2A9.toInt()
    private const val GRIS = 0xFF888780.toInt()
    private const val GRIS_FONCE = 0xFF5F5E5A.toInt()
    private const val GRIS_AVERSE = 0xFF9C9A93.toInt()
    private const val LUNE = 0xFFD3D1C7.toInt()

    private fun plein(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; style = Paint.Style.FILL }
    private fun trait(c: Int, l: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c; style = Paint.Style.STROKE; strokeWidth = l
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    private fun soleil(cv: Canvas, cx: Float, cy: Float, r: Float) {
        val t = trait(SOLEIL, 2.2f)
        for (i in 0 until 8) {
            val a = i * Math.PI / 4
            cv.drawLine(
                cx + (cos(a) * (r + 3)).toFloat(), cy + (sin(a) * (r + 3)).toFloat(),
                cx + (cos(a) * (r + 7)).toFloat(), cy + (sin(a) * (r + 7)).toFloat(), t,
            )
        }
        cv.drawCircle(cx, cy, r, plein(SOLEIL))
    }

    fun nuage(cv: Canvas, c: Int, dx: Float = 0f, dy: Float = 0f) {
        val p = plein(c)
        cv.drawCircle(36f + dx, 26f + dy, 8f, p)
        cv.drawCircle(25.5f + dx, 20f + dy, 11f, p)
        cv.drawCircle(15f + dx, 26f + dy, 8f, p)
        cv.drawRect(15f + dx, 26f + dy, 36f + dx, 34f + dy, p)
    }

    private fun gouttes(cv: Canvas, c: Int) {
        val t = trait(c, 2.2f)
        listOf(18f to 40f, 25f to 42f, 32f to 40f).forEach { (x, y) -> cv.drawLine(x, y - 3, x - 1.5f, y + 2, t) }
    }

    private fun flocons(cv: Canvas, c: Int) {
        val t = trait(c, 1.6f)
        listOf(18f to 40f, 25f to 42f, 32f to 40f).forEach { (x, y) ->
            cv.drawLine(x - 2.5f, y, x + 2.5f, y, t)
            cv.drawLine(x, y - 2.5f, x, y + 2.5f, t)
        }
    }

    private fun eclair(cv: Canvas) {
        val p = Path()
        p.moveTo(25f, 32f); p.lineTo(20f, 40f); p.lineTo(24f, 40f); p.lineTo(22f, 47f)
        p.lineTo(29f, 37f); p.lineTo(25f, 37f); p.lineTo(28f, 32f); p.close()
        cv.drawPath(p, plein(SOLEIL))
    }

    private fun lignesBrouillard(cv: Canvas, ys: List<Float>, x0: Float, x1: Float, l: Float) {
        val t = trait(GRIS_CLAIR, l)
        ys.forEachIndexed { i, y -> cv.drawLine(x0 + i * 2, y, x1 - i * 2, y, t) }
    }

    /** Icône standard. [fond] sert à découper le croissant de lune. */
    fun dessiner(cv: Canvas, type: String, x: Float, y: Float, taille: Float, fond: Int) {
        cv.save()
        cv.translate(x, y)
        cv.scale(taille / 48f, taille / 48f)
        when (type) {
            "soleil" -> soleil(cv, 24f, 24f, 8f)
            "eclaircies" -> { soleil(cv, 30f, 16f, 6f); nuage(cv, GRIS_CLAIR, 0f, 6f) }
            "nuageux" -> {
                cv.save(); cv.translate(6f, -4f); cv.scale(0.75f, 0.75f); nuage(cv, GRIS); cv.restore()
                nuage(cv, GRIS_CLAIR, 0f, 4f)
            }
            "pluie" -> { nuage(cv, GRIS, 0f, -4f); gouttes(cv, GOUTTE) }
            "averses" -> { soleil(cv, 31f, 13f, 6f); nuage(cv, GRIS_AVERSE, 0f, -2f); gouttes(cv, GOUTTE) }
            "neige" -> { nuage(cv, GRIS_CLAIR, 0f, -4f); flocons(cv, NEIGE) }
            "orage" -> { nuage(cv, GRIS_FONCE, 0f, -4f); eclair(cv) }
            "brouillard" -> { nuage(cv, GRIS, 0f, -6f); lignesBrouillard(cv, listOf(34f, 39f, 44f), 10f, 38f, 2.2f) }
            "lune" -> { cv.drawCircle(24f, 24f, 13f, plein(LUNE)); cv.drawCircle(31f, 18f, 11f, plein(fond)) }
            "lune-nuage" -> {
                cv.drawCircle(28f, 16f, 10f, plein(LUNE)); cv.drawCircle(34f, 11f, 8f, plein(fond))
                cv.save(); cv.translate(6f, 8f); cv.scale(0.85f, 0.85f); nuage(cv, GRIS); cv.restore()
            }
            else -> nuage(cv, GRIS_CLAIR)
        }
        cv.restore()
    }

    /** Largeur d'une icône de tendance pour une hauteur donnée. */
    fun largeurTendance(hauteur: Float) = hauteur * 62f / 56f

    /**
     * Icône de tendance : soleil + élément, et une flèche toujours vers la droite.
     * Flèche à gauche de l'élément = il arrive (« moins »), à droite = il s'en va (« mieux »).
     */
    fun tendance(cv: Canvas, tend: String, elem: String?, x: Float, y: Float, hauteur: Float, couleurFleche: Int) {
        val arrive = tend == "moins"
        cv.save()
        cv.translate(x, y)
        cv.scale(hauteur / 56f, hauteur / 56f)
        cv.translate(6f, 0f)
        soleil(cv, if (arrive) 34f else 12f, 15f, 7f)
        cv.save()
        cv.translate(if (arrive) 4f else -2f, 2f)
        when (elem) {
            "pluie" -> { nuage(cv, GRIS); gouttes(cv, GOUTTE) }
            "neige" -> { nuage(cv, GRIS_CLAIR); flocons(cv, NEIGE) }
            "orage" -> { nuage(cv, GRIS_FONCE); eclair(cv) }
            "brouillard" -> lignesBrouillard(cv, listOf(26f, 32f, 38f), 8f, 36f, 2.6f)
            else -> nuage(cv, GRIS_CLAIR)
        }
        cv.restore()
        val fx = if (arrive) -5f else 43f
        val t = trait(couleurFleche, 2f)
        cv.drawLine(fx, 47f, fx + 11, 47f, t)
        val p = Path()
        p.moveTo(fx + 7, 43f); p.lineTo(fx + 11, 47f); p.lineTo(fx + 7, 51f)
        cv.drawPath(p, t)
        cv.restore()
    }
}
