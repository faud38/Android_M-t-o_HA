package fr.meteowidget

import android.content.Context
import android.content.SharedPreferences

/** Réglages saisis dans l'application (restent uniquement sur le téléphone). */
object Reglages {
    private fun p(c: Context): SharedPreferences = c.getSharedPreferences("reglages", Context.MODE_PRIVATE)

    fun lieu(c: Context) = p(c).getString("lieu", "") ?: ""
    fun latitude(c: Context) = p(c).getString("lat", "") ?: ""
    fun longitude(c: Context) = p(c).getString("lon", "") ?: ""
    fun haUrl(c: Context) = (p(c).getString("ha_url", "") ?: "").trim().trimEnd('/')
    fun haJeton(c: Context) = (p(c).getString("ha_jeton", "") ?: "").trim()
    fun entMeteo(c: Context) = p(c).getString("ent_meteo", "") ?: ""
    fun entVigilance(c: Context) = p(c).getString("ent_vig", "") ?: ""
    fun entPluie(c: Context) = p(c).getString("ent_pluie", "") ?: ""

    fun enregistrer(c: Context, valeurs: Map<String, String>) {
        val e = p(c).edit()
        valeurs.forEach { (k, v) -> e.putString(k, v.trim()) }
        // Nouveaux réglages : on autorise de nouveau les appels à Home Assistant
        e.putBoolean("ha_refuse", false)
        e.apply()
    }

    /** Jeton refusé par Home Assistant : plus aucun appel tant que les réglages ne sont pas réenregistrés. */
    fun haRefuse(c: Context) = p(c).getBoolean("ha_refuse", false)
    fun setHaRefuse(c: Context) = p(c).edit().putBoolean("ha_refuse", true).apply()

    fun texte(c: Context, cle: String) = p(c).getString(cle, "") ?: ""

    fun derMaj(c: Context) = p(c).getLong("der_maj", 0)
    fun setDerMaj(c: Context, t: Long) = p(c).edit().putLong("der_maj", t).apply()
    fun erreur(c: Context) = p(c).getString("erreur", "") ?: ""
    fun setErreur(c: Context, e: String) = p(c).edit().putString("erreur", e).apply()
    fun sourceCalculs(c: Context) = p(c).getString("source_calculs", "") ?: ""
    fun setSourceCalculs(c: Context, s: String) = p(c).edit().putString("source_calculs", s).apply()
    fun derVerifMaj(c: Context) = p(c).getLong("der_verif", 0)
    fun setDerVerifMaj(c: Context, t: Long) = p(c).edit().putLong("der_verif", t).apply()
}
