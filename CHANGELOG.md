# Historique des versions

Le numéro de version de l'application est le numéro de compilation GitHub (v1, v2, v3…).

| Date | Modifications |
|---|---|
| 14/11/2026 | Première version : widget 7 jours (mêmes règles que la carte Home Assistant v11), détail heure par heure, réglages, vigilance et pluie dans l'heure via Home Assistant, mise à jour automatique |
| 14/11/2026 | Calculs lus dans le fichier partagé `meteo-calculs.js` de Home Assistant (même fichier que la carte), règles intégrées gardées en secours, source des calculs affichée dans l'application |
| 14/11/2026 | Six formats de widget : Grand, Mini, Carré, Bandeau, Moyen, Horloge. Copie de secours des règles mise à jour (version 2 : pluie le soir ou la nuit seulement → icône de tendance) |
| 14/11/2026 | Recherche de mise à jour : secours par la page web GitHub si l'API répond en erreur ; message d'erreur complet affiché |
| 15/11/2026 | Écran de détail : barre des 7 jours en haut, détail heure par heure du jour choisi en dessous (ouvert sur le jour touché) |
| 15/11/2026 | Écran de détail : indice de fiabilité (5 points de couleur) sous chaque jour |
| 15/11/2026 | Bandeau et Moyen : toucher la météo du jour ouvre son détail (au lieu d'actualiser) ; bouton « Actualiser » dans l'écran de détail |
| 15/11/2026 | Écran de détail : glisser le doigt à gauche ou à droite pour passer au jour suivant ou précédent |
| 15/11/2026 | Notification de mise à jour avec la liste des nouveautés depuis la version installée ; vérification toutes les 3 h, une seule notification par version |
| 15/11/2026 | Écran de détail : les jours défilent en suivant le doigt (pages), plus fluide |
| 15/11/2026 | Nuages bas demandés à Open-Meteo ; liste des données horaires lue dans meteo-calculs.js (plus de nouvelle version de l'appli pour en ajouter) ; copie de secours des règles en version 3 |
