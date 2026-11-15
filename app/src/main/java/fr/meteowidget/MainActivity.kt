package fr.meteowidget

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/** Écran de réglages : position, Home Assistant, mise à jour. */
class MainActivity : Activity() {
    private val champs = linkedMapOf<String, EditText>()
    private lateinit var statut: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        col.addView(TextView(this).apply { text = "Météo widget"; textSize = 24f })
        statut = TextView(this).apply { textSize = 13f; setPadding(0, dp(6), 0, dp(12)) }
        col.addView(statut)

        titre(col, "Lieu des prévisions")
        champ(col, "lieu", "Nom affiché", "Ma ville", InputType.TYPE_CLASS_TEXT)
        val nombre = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        champ(col, "lat", "Latitude", "45.1234", nombre)
        champ(col, "lon", "Longitude", "5.1234", nombre)

        titre(col, "Home Assistant (facultatif : pluie dans l'heure, vigilance, temps actuel)")
        champ(col, "ha_url", "Adresse de Home Assistant", "https://ha.mondomaine.fr", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        champ(col, "ha_jeton", "Jeton d'accès longue durée", "eyJ…", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        champ(col, "ent_meteo", "Entité météo", "weather.ma_ville", InputType.TYPE_CLASS_TEXT)
        champ(col, "ent_vig", "Entité vigilance", "sensor.XX_weather_alert", InputType.TYPE_CLASS_TEXT)
        champ(col, "ent_pluie", "Entité pluie dans l'heure", "sensor.ma_ville_next_rain", InputType.TYPE_CLASS_TEXT)

        col.addView(Button(this).apply {
            text = "Enregistrer et actualiser"
            setOnClickListener { enregistrer() }
        })
        col.addView(Button(this).apply {
            text = "Vérifier les mises à jour"
            setOnClickListener { verifierMaj() }
        })
        col.addView(TextView(this).apply {
            textSize = 13f
            setPadding(0, dp(16), 0, 0)
            text = "Pour ajouter le widget : appui long sur l'écran d'accueil → Widgets → Météo widget.\n" +
                "Touchez la météo d'un widget pour la semaine et le détail heure par heure ; glissez le doigt sur les heures pour changer de jour."
        })

        setContentView(ScrollView(this).apply {
            addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })

        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        if (intent?.action == MiseAJour.ACTION) verifierMaj()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == MiseAJour.ACTION) verifierMaj()
    }

    override fun onResume() {
        super.onResume()
        majStatut()
    }

    private fun titre(col: LinearLayout, t: String) {
        col.addView(TextView(this).apply { text = t; textSize = 16f; setPadding(0, dp(18), 0, dp(4)) })
    }

    private fun champ(col: LinearLayout, cle: String, libelle: String, exemple: String, type: Int) {
        col.addView(TextView(this).apply { text = libelle; textSize = 13f; setPadding(0, dp(8), 0, 0) })
        val e = EditText(this).apply {
            hint = exemple
            inputType = type
            setText(Reglages.texte(this@MainActivity, cle))
            setSingleLine(true)
        }
        champs[cle] = e
        col.addView(e)
    }

    private fun enregistrer() {
        Reglages.enregistrer(this, champs.mapValues { it.value.text.toString() })
        Planif.periodique(this)
        Planif.maintenant(this)
        Toast.makeText(this, "Enregistré, actualisation en cours…", Toast.LENGTH_SHORT).show()
        statut.postDelayed({ majStatut() }, 5000)
    }

    private fun majStatut() {
        val der = Reglages.derMaj(this)
        val quand = if (der > 0) SimpleDateFormat("dd/MM à HH:mm", Locale.FRANCE).format(Date(der)) else "jamais"
        val err = Reglages.erreur(this)
        val src = Reglages.sourceCalculs(this)
        statut.text = "Version ${BuildConfig.VERSION_NAME} · dernière actualisation : $quand" +
            (if (src.isNotBlank()) "\nCalculs : $src" else "") +
            (if (err.isNotBlank()) "\n⚠ $err" else "")
    }

    private fun verifierMaj() {
        Toast.makeText(this, "Recherche d'une mise à jour…", Toast.LENGTH_SHORT).show()
        thread {
            try {
                val v = MiseAJour.disponible()
                runOnUiThread {
                    if (v == null) {
                        Toast.makeText(this, "Vous avez la dernière version (${BuildConfig.VERSION_NAME})", Toast.LENGTH_LONG).show()
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("Version ${v.numero} disponible")
                            .setMessage(v.notes.ifBlank { "Une nouvelle version est prête." })
                            .setPositiveButton("Installer") { _, _ -> installer(v) }
                            .setNegativeButton("Plus tard", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    AlertDialog.Builder(this).setTitle("Vérification impossible")
                        .setMessage("${e.message}\n\nDépôt : ${BuildConfig.DEPOT}\nVersion installée : ${BuildConfig.VERSION_NAME}")
                        .setPositiveButton("OK", null).show()
                }
            }
        }
    }

    private fun installer(v: MiseAJour.Version) {
        Toast.makeText(this, "Téléchargement de la version ${v.numero}…", Toast.LENGTH_SHORT).show()
        thread {
            try {
                val f = MiseAJour.telecharger(this, v)
                runOnUiThread {
                    if (!MiseAJour.installer(this, f)) {
                        Toast.makeText(
                            this,
                            "Autorisez « Météo widget » à installer des applications, puis touchez à nouveau « Vérifier les mises à jour ».",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    AlertDialog.Builder(this).setTitle("Téléchargement impossible")
                        .setMessage(e.message ?: e.toString()).setPositiveButton("OK", null).show()
                }
            }
        }
    }
}
