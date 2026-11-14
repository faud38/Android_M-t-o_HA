# Météo widget

Widget Android de prévisions sur 7 jours, version téléphone de la carte Home Assistant « Météo » :

- **3 modèles** (Météo-France AROME/ARPÈGE, ECMWF, ICON) via [Open-Meteo](https://open-meteo.com), valeur retenue = médiane ;
- **ensoleillement** heure par heure, plafonné par le ciel le plus dégagé vu par les modèles ;
- **icône du jour** calculée sur les heures de jour, pluie de nuit ou du soir signalée à part ;
- **tendance** matin → après-midi (flèche avant l'élément : il arrive ; après : il s'en va) ;
- **indice de fiabilité** /5 selon l'accord entre les modèles ;
- en option, depuis **Home Assistant** : temps actuel, vigilance Météo-France, pluie dans l'heure ;
- **détail heure par heure** en touchant un jour, **actualisation** en touchant l'en-tête ;
- **mise à jour automatique** depuis les versions publiées sur ce dépôt.

## Calculs partagés avec Home Assistant

Les règles de prévision (médiane, plafond du soleil, icônes, tendance, fiabilité) sont dans **un seul fichier**,
`meteo-calculs.js`, rangé dans Home Assistant (`/homeassistant/www`) et utilisé à la fois par la carte et par le widget.
Le widget le télécharge à chaque actualisation (`<adresse HA>/local/meteo-calculs.js`) et l'exécute avec le moteur
JavaScript d'Android. **Modifier ce fichier suffit** : aucune nouvelle version de l'application n'est nécessaire.
Si le fichier est injoignable, le widget utilise la dernière copie reçue, ou à défaut les règles intégrées à l'application.
L'écran de l'application indique la source utilisée (« Calculs : fichier Home Assistant, version N du … »).

Aucune donnée personnelle dans ce dépôt : position, adresse et jeton Home Assistant se saisissent dans l'application et restent sur le téléphone.

## Installation — procédure

| Version | Date | Modifications |
|---|---|---|
| 1.0 | 14/11/2026 | Première rédaction |
| 1.1 | 14/11/2026 | Ajout des calculs partagés avec Home Assistant |

### 1. Clé de signature (une seule fois)

La clé garantit que chaque nouvelle version peut remplacer la précédente sur le téléphone.
Dans le dépôt : **Settings → Secrets and variables → Actions → New repository secret**, créer :

- `KEYSTORE_PASSWORD` : le mot de passe de la clé ;
- `KEYSTORE_BASE64` : le contenu de la clé, encodé en base64 (une seule ligne).

⚠️ Conserver ces deux valeurs en lieu sûr : sans elles, les mises à jour ne pourront plus s'installer par-dessus l'application.

### 2. Compilation

Chaque envoi sur la branche `main` lance la compilation (onglet **Actions**). À la fin, une nouvelle
version apparaît dans **Releases** avec le fichier `meteo-widget-vN.apk`. On peut aussi la lancer à la main :
**Actions → Compilation → Run workflow**.

### 3. Installation sur le téléphone

1. Ouvrir la dernière version dans **Releases** depuis le téléphone et télécharger l'APK.
2. Autoriser l'installation depuis le navigateur si Android le demande, puis installer.
3. Ouvrir **Météo widget**, saisir le nom du lieu, la latitude et la longitude, et éventuellement Home Assistant
   (adresse, jeton d'accès longue durée créé dans *Profil → Sécurité*, entités météo, vigilance et pluie dans l'heure).
4. **Enregistrer et actualiser**.
5. Appui long sur l'écran d'accueil → **Widgets** → **Météo widget**.

### 4. Mises à jour

L'application vérifie deux fois par jour s'il existe une version plus récente et envoie une notification.
On peut aussi vérifier à la main : **Vérifier les mises à jour** dans l'application.
La première fois, Android demande d'autoriser « Météo widget » à installer des applications.
