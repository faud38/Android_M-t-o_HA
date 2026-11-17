package fr.meteowidget

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Copie de secours des règles de meteo-calculs.js (version 8), utilisée si le fichier
 * de Home Assistant est injoignable :
 *  - valeurs = médiane des 3 modèles (Météo-France, ECMWF, ICON)
 *  - soleil heure par heure = médiane, plafonnée par le ciel le plus dégagé vu par les modèles
 *  - icône du jour calculée sur les heures de jour, pluie de nuit/soir signalée à part
 *  - tendance matin → après-midi, fiabilité /5 selon l'accord des modèles
 */

val MODELES = listOf("meteofrance_seamless", "ecmwf_ifs025", "icon_seamless")

data class Heure(
    val t: String, val ic: String, val pluie: Double, val nuages: Double, val jour: Boolean,
    val codes: List<Int>, val soleil: Double, val temp: Double?, val vent: Double?, val dir: Double?,
)

data class Jour(
    val date: LocalDate, val tmax: Double?, val tmin: Double?, val pluie: Double, val pluieJour: Double?,
    val pluieNuit: Double, val pluieSoir: Double, val tendance: String?, val elem: String?,
    val nuages: Double, val soleil: Double, val duree: Double, val icone: String, val note: Int,
    val vent: Double?, val dir: Double?,
)

data class Actuel(val temp: Double?, val code: Int?, val jour: Boolean)

object Calculs {
    fun mediane(v: List<Double>): Double? {
        if (v.isEmpty()) return null
        val s = v.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2
    }

    fun ecart(v: List<Double>): Double = if (v.size < 2) 0.0 else v.max() - v.min()

    fun directionMoyenne(d: List<Double>): Double? {
        if (d.isEmpty()) return null
        var x = 0.0
        var y = 0.0
        d.forEach {
            x += cos(Math.toRadians(it))
            y += sin(Math.toRadians(it))
        }
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    fun estNeige(c: Int) = c in 71..77 || c == 85 || c == 86
    fun estBrouillard(c: Int) = c == 45 || c == 48

    private fun lire(a: JSONArray?, i: Int): Double? {
        if (a == null || i >= a.length() || a.isNull(i)) return null
        val v = a.optDouble(i)
        return if (v.isNaN()) null else v
    }

    private fun valeurs(o: JSONObject, variable: String, i: Int): List<Double> =
        MODELES.mapNotNull { m -> lire(o.optJSONArray("${variable}_$m") ?: o.optJSONArray(variable), i) }

    fun heures(json: JSONObject): List<Heure> {
        val h = json.optJSONObject("hourly") ?: return emptyList()
        val t = h.optJSONArray("time") ?: return emptyList()
        return (0 until t.length()).map { i ->
            val codes = valeurs(h, "weather_code", i).map { it.toInt() }
            fun maj(test: (Int) -> Boolean) = codes.count(test) > codes.size / 2.0
            val pluie = mediane(valeurs(h, "precipitation", i)) ?: 0.0
            val cc = valeurs(h, "cloud_cover", i)
            val nuages = mediane(cc) ?: 0.0
            val jour = (valeurs(h, "is_day", i).firstOrNull() ?: 1.0) == 1.0
            val nuagesMin = cc.minOrNull() ?: nuages
            // Soleil de chaque modèle plafonné par ses propres nuages bas, puis médiane (v3)
            val soleils = MODELES.mapNotNull { m ->
                val s = lire(h.optJSONArray("sunshine_duration_$m") ?: h.optJSONArray("sunshine_duration"), i) ?: return@mapNotNull null
                val bas = lire(h.optJSONArray("cloud_cover_low_$m") ?: h.optJSONArray("cloud_cover_low"), i)
                if (bas == null) s else min(s, (1 - bas / 100) * 3600)
            }
            val soleil = if (jour) min(mediane(soleils) ?: 0.0, (1 - nuagesMin / 100) * 3600) else 0.0
            val part = soleil / 3600
            val ic = when {
                pluie >= 0.3 && codes.any { it >= 95 } -> "orage"
                pluie >= 0.1 && maj { estNeige(it) } -> "neige"
                pluie >= 0.2 -> "pluie"
                maj { estBrouillard(it) } -> "brouillard"
                jour -> if (part >= 0.75) "soleil" else if (part >= 0.3) "eclaircies" else "nuageux"
                nuages >= 75 -> "nuageux"
                nuages >= 35 -> "lune-nuage"
                else -> "lune"
            }
            Heure(
                t.getString(i), ic, pluie, nuages, jour, codes, max(0.0, soleil),
                mediane(valeurs(h, "temperature_2m", i)), mediane(valeurs(h, "wind_speed_10m", i)),
                directionMoyenne(valeurs(h, "wind_direction_10m", i)),
            )
        }
    }

    fun jours(json: JSONObject, heures: List<Heure>): List<Jour> {
        val d = json.optJSONObject("daily") ?: return emptyList()
        val times = d.optJSONArray("time") ?: return emptyList()
        val auj = LocalDate.now().toString()
        val res = mutableListOf<Jour>()
        for (i in 0 until times.length()) {
            val date = times.getString(i)
            if (date < auj) continue
            fun vals(v: String) = valeurs(d, v, i)

            val tmaxs = vals("temperature_2m_max")
            val pluies = vals("precipitation_sum")
            val soleils = vals("sunshine_duration")
            val codes = vals("weather_code").map { it.toInt() }
            fun majorite(test: (Int) -> Boolean) = codes.count(test) > codes.size / 2.0

            val nuages = mediane(vals("cloud_cover_mean")) ?: 0.0
            val duree = mediane(vals("daylight_duration")) ?: 36000.0
            val pluie = mediane(pluies) ?: 0.0
            val plafond = (1 - nuages / 100) * duree
            val soleil = min(mediane(soleils) ?: 0.0, plafond)

            var part = soleil / duree
            var icone: String
            var pluieJour: Double? = null
            var pluieNuit = 0.0
            var pluieSoir = 0.0
            var tendance: String? = null
            var elem: String? = null
            var nuagesAff = nuages
            var soleilAff = soleil

            val heuresJour = heures.filter { it.t.startsWith(date) && it.jour }
            if (heuresJour.size >= 6) {
                // v7 : seules les heures d'au moins 0,2 mm comptent, comme pour les icônes horaires
                val pj = heuresJour.sumOf { if (it.pluie >= 0.2) it.pluie else 0.0 }
                pluieJour = pj
                val toutes = heures.filter { it.t.startsWith(date) }
                val debut = heuresJour.first().t
                val fin = heuresJour.last().t
                pluieNuit = toutes.filter { it.t < debut }.sumOf { it.pluie }
                pluieSoir = toutes.filter { it.t > fin }.sumOf { it.pluie }
                val heuresPluie = heuresJour.count { it.pluie >= 0.2 }
                nuagesAff = heuresJour.sumOf { it.nuages } / heuresJour.size
                val soleilHoraire = heuresJour.sumOf { min(it.soleil, 3600.0) }
                soleilAff = min(soleilHoraire, duree)
                part = soleilAff / duree

                val moitie = heuresJour.size / 2
                fun beau(hs: List<Heure>) =
                    hs.sumOf { min(it.soleil, 3600.0) / 3600 - (if (it.pluie >= 0.2) 0.5 else 0.0) } / hs.size
                val ecartTendance = beau(heuresJour.takeLast(moitie)) - beau(heuresJour.take(moitie))
                if (ecartTendance >= 0.35) tendance = "mieux"
                else if (ecartTendance <= -0.35) tendance = "moins"

                val codesJour = heuresJour.flatMap { it.codes }
                fun majJour(test: (Int) -> Boolean) = codesJour.count(test) > codesJour.size / 2.0
                icone = when {
                    pj >= 1 && codesJour.any { it >= 95 } -> "orage"
                    pj >= 0.5 && majJour { estNeige(it) } -> "neige"
                    pj >= 1 && heuresPluie >= 3 && part < 0.3 -> "pluie"
                    pj >= 0.5 -> if (part >= 0.3) "averses" else "pluie"
                    majJour { estBrouillard(it) } -> "brouillard"
                    part >= 0.75 -> "soleil"
                    part >= 0.3 -> "eclaircies"
                    else -> "nuageux"
                }
                if (tendance != null) {
                    val hs = if (tendance == "moins") heuresJour.takeLast(moitie) else heuresJour.take(moitie)
                    val cw = hs.flatMap { it.codes }
                    val pw = hs.sumOf { it.pluie }
                    fun majW(test: (Int) -> Boolean) = cw.count(test) > cw.size / 2.0
                    elem = when {
                        pw >= 0.5 && cw.any { it >= 95 } -> "orage"
                        pw >= 0.3 && majW { estNeige(it) } -> "neige"
                        pw >= 0.5 -> "pluie"
                        majW { estBrouillard(it) } -> "brouillard"
                        else -> "nuage"
                    }
                }
                // v4 : précipitations ou brouillard sur une seule moitié de la journée
                val matin = heuresJour.take(moitie)
                val aprem = heuresJour.takeLast(moitie)
                fun elemPrecip(hs: List<Heure>): String {
                    val cw = hs.flatMap { it.codes }
                    if (cw.any { it >= 95 }) return "orage"
                    if (cw.count { estNeige(it) } > cw.size / 2.0) return "neige"
                    return "pluie"
                }
                fun brouillard(hs: List<Heure>): Boolean {
                    val cw = hs.flatMap { it.codes }
                    return cw.isNotEmpty() && cw.count { it == 45 || it == 48 } > cw.size / 2.0
                }
                val pM = matin.sumOf { if (it.pluie >= 0.2) it.pluie else 0.0 }
                val pA = aprem.sumOf { if (it.pluie >= 0.2) it.pluie else 0.0 }
                val uneMoitie = if (pM >= 0.5 && pA < 0.2) matin else if (pA >= 0.5 && pM < 0.2) aprem else null
                if (uneMoitie != null && part < 0.3) {
                    // Journée grise : l'icône de tendance montrerait un soleil (v5)
                    val el = elemPrecip(uneMoitie)
                    tendance = null; elem = null
                    icone = if (el == "pluie") "nuageux" else el
                } else if (pM >= 0.5 && pA < 0.2) {
                    tendance = "mieux"; elem = elemPrecip(matin)
                } else if (pA >= 0.5 && pM < 0.2) {
                    tendance = "moins"; elem = elemPrecip(aprem)
                } else if (part >= 0.3 && pj < 0.5 && brouillard(matin) && !brouillard(aprem)) {
                    tendance = "mieux"; elem = "brouillard"
                } else if (part >= 0.3 && pj < 0.5 && brouillard(aprem) && !brouillard(matin)) {
                    tendance = "moins"; elem = "brouillard"
                }
                // Journée sèche et ensoleillée, encadrée de pluie : le soir (elle arrive)
                // ou la nuit précédente (elle s'en va) — règle v2 de meteo-calculs.js
                if (part >= 0.3 && pj < 0.5) {
                    if (pluieSoir >= 0.5 && pluieNuit < 0.5) {
                        tendance = "moins"; elem = "pluie"
                    }
                }
                // v6 : l'icône de tendance montre un soleil → sur une journée grise, seulement si
                // la « belle » moitié (l'après-midi si ça s'améliore, le matin sinon) a vraiment du soleil
                if (tendance != null && part < 0.3) {
                    val belle = if (tendance == "mieux") heuresJour.takeLast(moitie) else heuresJour.take(moitie)
                    val sol = belle.sumOf { min(it.soleil, 3600.0) } / (maxOf(belle.size, 1) * 3600.0)
                    if (sol < 0.3) { tendance = null; elem = null }
                }
            } else {
                icone = when {
                    pluie >= 1 && codes.any { it >= 95 } -> "orage"
                    pluie >= 1 && majorite { estNeige(it) } -> "neige"
                    pluie >= 1 -> "pluie"
                    majorite { estBrouillard(it) } -> "brouillard"
                    part >= 0.75 -> "soleil"
                    part >= 0.3 -> "eclaircies"
                    else -> "nuageux"
                }
            }

            // Indice de fiabilité
            var note = 5
            if (tmaxs.size < 3) note -= 1
            val et = ecart(tmaxs)
            if (et > 5) note -= 2 else if (et > 3) note -= 1
            if (pluies.any { it >= 1 } && pluies.any { it < 0.5 }) note -= 1
            val es = ecart(soleils) / 3600
            if (es > 5) note -= 2 else if (es > 3) note -= 1
            note = note.coerceIn(1, 5)

            res.add(
                Jour(
                    LocalDate.parse(date), mediane(tmaxs), mediane(vals("temperature_2m_min")), pluie, pluieJour,
                    pluieNuit, pluieSoir, tendance, elem, nuagesAff, soleilAff, duree, icone, note,
                    mediane(vals("wind_speed_10m_max")), directionMoyenne(vals("wind_direction_10m_dominant")),
                ),
            )
        }
        return res
    }

    /** Lit le résultat de MeteoCalculs.calculer() (fichier partagé meteo-calculs.js). */
    fun depuisJs(o: JSONObject): Pair<List<Jour>, List<Heure>> {
        fun num(x: JSONObject, k: String): Double? {
            if (!x.has(k) || x.isNull(k)) return null
            val v = x.optDouble(k)
            return if (v.isNaN()) null else v
        }
        fun txt(x: JSONObject, k: String): String? = if (!x.has(k) || x.isNull(k)) null else x.optString(k)
        val jours = mutableListOf<Jour>()
        o.optJSONArray("jours")?.let { a ->
            for (i in 0 until a.length()) {
                val x = a.getJSONObject(i)
                jours.add(
                    Jour(
                        LocalDate.parse(x.optString("jour")), num(x, "tmax"), num(x, "tmin"), num(x, "pluie") ?: 0.0,
                        num(x, "pluieJour"), num(x, "pluieNuit") ?: 0.0, num(x, "pluieSoir") ?: 0.0,
                        txt(x, "tendance"), txt(x, "elemTendance"), num(x, "nuages") ?: 0.0, num(x, "soleil") ?: 0.0,
                        num(x, "duree") ?: 36000.0, txt(x, "icone") ?: "nuageux", x.optInt("note", 3),
                        num(x, "vent"), num(x, "dir"),
                    ),
                )
            }
        }
        val heures = mutableListOf<Heure>()
        o.optJSONArray("heures")?.let { a ->
            for (i in 0 until a.length()) {
                val x = a.getJSONObject(i)
                val cs = x.optJSONArray("codes")
                val codes = if (cs == null) emptyList() else (0 until cs.length()).map { cs.optInt(it) }
                heures.add(
                    Heure(
                        x.optString("t"), txt(x, "ic") ?: "nuageux", num(x, "pluie") ?: 0.0, num(x, "nuages") ?: 0.0,
                        x.optBoolean("jour", true), codes, num(x, "soleil") ?: 0.0,
                        num(x, "temp"), num(x, "vent"), num(x, "dir"),
                    ),
                )
            }
        }
        return jours to heures
    }

    fun actuel(json: JSONObject): Actuel? {
        val c = json.optJSONObject("current") ?: return null
        fun v(nom: String): List<Double> = MODELES.mapNotNull { m ->
            val x = c.optDouble("${nom}_$m")
            if (x.isNaN()) null else x
        }.ifEmpty { listOfNotNull(c.optDouble(nom).takeIf { !it.isNaN() }) }
        val codes = v("weather_code")
        return Actuel(mediane(v("temperature_2m")), mediane(codes)?.toInt(), (v("is_day").firstOrNull() ?: 1.0) == 1.0)
    }
}
