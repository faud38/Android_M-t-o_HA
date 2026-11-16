package fr.meteowidget

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Vigilance Météo-France lue dans Home Assistant. */
data class Vigilance(val niveau: Int, val phenomenes: List<Pair<String, Int>>)

/** Pluie dans l'heure lue dans Home Assistant. */
data class PluieHeure(val texte: String, val pluie: Boolean, val segments: List<Pair<Int, Int>>)

/** Tout ce qu'il faut pour dessiner le widget. */
data class Etat(
    val jours: List<Jour>,
    val heures: List<Heure>,
    val actuel: Actuel?,
    val tempHa: Double?,
    val etatHa: String?,
    val vigilance: Vigilance?,
    val pluieHeure: PluieHeure?,
    val derMaj: Long,
    val erreur: String,
    val configure: Boolean,
)

object Donnees {
    private const val FICHIER_OM = "openmeteo.json"
    private const val FICHIER_HA = "homeassistant.json"
    private const val FICHIER_JS = "meteo-calculs.js"
    private const val FICHIER_CALCUL = "calcul.json"
    private val NIVEAUX = mapOf("Vert" to 0, "Jaune" to 1, "Orange" to 2, "Rouge" to 3)
    private val INTENSITES = mapOf("Temps sec" to 0, "Pluie faible" to 1, "Pluie modérée" to 2, "Pluie forte" to 3)

    private const val VARS_DEFAUT = "temperature_2m,precipitation,cloud_cover,cloud_cover_low,wind_speed_10m," +
        "wind_direction_10m,weather_code,is_day,sunshine_duration"

    /** Variables horaires : lues dans la copie de meteo-calculs.js (VARS_HORAIRES), sinon liste intégrée. */
    private fun varsHoraires(c: Context?): String {
        try {
            val js = c?.let { File(it.filesDir, FICHIER_JS) }?.takeIf { it.exists() }?.readText() ?: return VARS_DEFAUT
            val bloc = Regex("""VARS_HORAIRES\s*=\s*\[([^\]]*)\]""").find(js)?.groupValues?.get(1) ?: return VARS_DEFAUT
            val noms = Regex("""["']([a-z0-9_]+)["']""").findAll(bloc).map { it.groupValues[1] }.toList()
            return if (noms.size >= 5 && "sunshine_duration" in noms) noms.joinToString(",") else VARS_DEFAUT
        } catch (_: Exception) {
            return VARS_DEFAUT
        }
    }

    fun urlOpenMeteo(lat: String, lon: String, c: Context? = null): String {
        val la = lat.replace(',', '.').toDouble()
        val lo = lon.replace(',', '.').toDouble()
        return "https://api.open-meteo.com/v1/forecast" +
            String.format(Locale.US, "?latitude=%.4f&longitude=%.4f", la, lo) +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunshine_duration,daylight_duration," +
            "precipitation_sum,cloud_cover_mean,wind_speed_10m_max,wind_direction_10m_dominant" +
            "&hourly=" + varsHoraires(c) +
            "&current=temperature_2m,weather_code,is_day" +
            "&models=" + MODELES.joinToString(",") +
            "&wind_speed_unit=kmh&timezone=auto&forecast_days=7"
    }

    /** Télécharge les prévisions (Open-Meteo) et les infos Home Assistant, puis les met en cache. */
    fun actualiser(c: Context) {
        val lat = Reglages.latitude(c)
        val lon = Reglages.longitude(c)
        if (lat.isBlank() || lon.isBlank()) throw IllegalStateException("Position non configurée")
        val om = Reseau.texte(urlOpenMeteo(lat, lon, c))
        JSONObject(om) // vérifie que la réponse est lisible
        File(c.filesDir, FICHIER_OM).writeText(om)

        val url = Reglages.haUrl(c)
        val jeton = Reglages.haJeton(c)
        val erreurs = mutableListOf<String>()
        if (url.isNotBlank() && jeton.isNotBlank() && Reglages.haRefuse(c)) {
            erreurs.add("Home Assistant : jeton refusé. Vérifiez-le puis touchez « Enregistrer et actualiser ».")
        } else if (url.isNotBlank() && jeton.isNotBlank()) {
            val ha = JSONObject()
            val entites = mapOf(
                "meteo" to Reglages.entMeteo(c),
                "vigilance" to Reglages.entVigilance(c),
                "pluie" to Reglages.entPluie(c),
            )
            // Un jeton refusé (401/403) compte comme une tentative de connexion ratée :
            // Home Assistant bannit l'adresse IP après quelques essais. On s'arrête donc
            // au premier refus et on ne réessaie plus tant que les réglages n'ont pas changé.
            for ((cle, ent) in entites) {
                if (ent.isBlank()) continue
                try {
                    val txt = Reseau.texte("$url/api/states/$ent", mapOf("Authorization" to "Bearer $jeton"))
                    ha.put(cle, JSONObject(txt))
                } catch (e: Exception) {
                    val m = e.message ?: ""
                    if (m.startsWith("HTTP 401") || m.startsWith("HTTP 403")) {
                        Reglages.setHaRefuse(c)
                        erreurs.add("Home Assistant : jeton refusé ($m). Appels suspendus pour éviter un bannissement. Vérifiez le jeton puis touchez « Enregistrer et actualiser ».")
                        break
                    }
                    erreurs.add("Home Assistant ($ent) : $m")
                }
            }
            if (ha.length() > 0) File(c.filesDir, FICHIER_HA).writeText(ha.toString())
        }
        calculer(c, om, erreurs)
        Reglages.setDerMaj(c, System.currentTimeMillis())
        Reglages.setErreur(c, erreurs.joinToString("\n"))
    }

    /**
     * Calcule les prévisions avec le fichier partagé meteo-calculs.js de Home Assistant
     * (le même que la carte). Sans lui, l'application utilise sa copie intégrée des règles.
     */
    private fun calculer(c: Context, om: String, erreurs: MutableList<String>) {
        val fJs = File(c.filesDir, FICHIER_JS)
        val fCalc = File(c.filesDir, FICHIER_CALCUL)
        val url = Reglages.haUrl(c)
        if (url.isNotBlank()) {
            try {
                val js = Reseau.texte("$url/local/meteo-calculs.js?t=${System.currentTimeMillis() / 60000}")
                if (js.contains("MeteoCalculs")) fJs.writeText(js)
            } catch (e: Exception) {
                if (!fJs.exists()) erreurs.add("Fichier de calculs Home Assistant : ${e.message}")
            }
        }
        if (!fJs.exists()) {
            fCalc.delete()
            Reglages.setSourceCalculs(c, "règles intégrées à l'application")
            return
        }
        try {
            val code = fJs.readText() + "\n;JSON.stringify(MeteoCalculs.calculer(" + om + ", \"" + LocalDate.now() + "\"));"
            val res = MoteurJs.executer(c, code)
            val o = JSONObject(res)
            fCalc.writeText(res)
            Reglages.setSourceCalculs(
                c,
                "fichier Home Assistant, version ${o.optInt("version")} du ${o.optString("date_version")}",
            )
        } catch (e: Exception) {
            fCalc.delete()
            erreurs.add("Calculs Home Assistant non exécutés (${e.message}) : règles intégrées utilisées")
            Reglages.setSourceCalculs(c, "règles intégrées à l'application")
        }
    }

    fun charger(c: Context): Etat {
        val configure = Reglages.latitude(c).isNotBlank() && Reglages.longitude(c).isNotBlank()
        var jours = emptyList<Jour>()
        var heures = emptyList<Heure>()
        var actuel: Actuel? = null
        try {
            val f = File(c.filesDir, FICHIER_OM)
            if (f.exists()) {
                val json = JSONObject(f.readText())
                actuel = Calculs.actuel(json)
                val fc = File(c.filesDir, FICHIER_CALCUL)
                if (fc.exists()) {
                    val (j, h) = Calculs.depuisJs(JSONObject(fc.readText()))
                    jours = j.filter { !it.date.isBefore(LocalDate.now()) }
                    heures = h
                } else {
                    heures = Calculs.heures(json)
                    jours = Calculs.jours(json, heures)
                }
            }
        } catch (_: Exception) {
        }

        var tempHa: Double? = null
        var etatHa: String? = null
        var vig: Vigilance? = null
        var ph: PluieHeure? = null
        try {
            val f = File(c.filesDir, FICHIER_HA)
            if (f.exists() && Reglages.haUrl(c).isNotBlank()) {
                val ha = JSONObject(f.readText())
                ha.optJSONObject("meteo")?.let { m ->
                    etatHa = m.optString("state").ifBlank { null }
                    val t = m.optJSONObject("attributes")?.optDouble("temperature") ?: Double.NaN
                    if (!t.isNaN()) tempHa = t
                }
                ha.optJSONObject("vigilance")?.let { vig = vigilance(it) }
                ha.optJSONObject("pluie")?.let { ph = pluieHeure(it) }
            }
        } catch (_: Exception) {
        }
        return Etat(jours, heures, actuel, tempHa, etatHa, vig, ph, Reglages.derMaj(c), Reglages.erreur(c), configure)
    }

    private fun vigilance(o: JSONObject): Vigilance? {
        val niveau = NIVEAUX[o.optString("state")] ?: return null
        val attrs = o.optJSONObject("attributes") ?: JSONObject()
        val ph = mutableListOf<Pair<String, Int>>()
        attrs.keys().forEach { k ->
            val n = NIVEAUX[attrs.optString(k)]
            if (n != null && n > 0) ph.add(k to n)
        }
        return Vigilance(niveau, ph.sortedByDescending { it.second })
    }

    private fun pluieHeure(o: JSONObject): PluieHeure? {
        val attrs = o.optJSONObject("attributes") ?: return null
        val prev = attrs.optJSONObject("1_hour_forecast") ?: return null
        val pas = mutableListOf<Pair<Int, String>>()
        prev.keys().forEach { k ->
            val min = k.trim().substringBefore(" ").toIntOrNull()
            if (min != null) pas.add(min to prev.optString(k))
        }
        if (pas.isEmpty()) return null
        pas.sortBy { it.first }
        val ref = try {
            OffsetDateTime.parse(attrs.optString("forecast_time_ref")).atZoneSameInstant(ZoneId.systemDefault())
        } catch (_: Exception) {
            null
        }
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val premier = pas.firstOrNull { (INTENSITES[it.second] ?: 1) > 0 }
        val texte = when {
            premier == null -> "Pas de pluie dans l'heure"
            premier.first == 0 -> "${premier.second} en ce moment"
            ref != null -> "${premier.second} à partir de ${ref.plusMinutes(premier.first.toLong()).format(fmt)}"
            else -> "${premier.second} dans ${premier.first} min"
        }
        val segments = pas.mapIndexed { i, p ->
            val fin = if (i + 1 < pas.size) pas[i + 1].first else 60
            (fin - p.first) to (INTENSITES[p.second] ?: 1)
        }
        return PluieHeure(texte, premier != null, segments)
    }
}
