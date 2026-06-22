# Installation pas à pas — AnimIA MVP

Ce guide est écrit pour quelqu'un qui n'a **jamais utilisé Android Studio**. Suis les étapes dans l'ordre.

---

## 1. Installer Android Studio

1. Va sur https://developer.android.com/studio et clique **Download Android Studio**.
2. Lance l'installeur, garde toutes les options par défaut. Au premier lancement, Android Studio va télécharger le SDK Android (~3 Go, prévoir 10–15 min).
3. Quand le dashboard "Welcome to Android Studio" s'affiche, ferme-le, on va y revenir.

---

## 2. Ouvrir le projet AnimIA

1. Dans Android Studio, clique **Open** (ou **File → Open**).
2. Sélectionne le dossier `AnimIAMVP` (celui qui contient `settings.gradle.kts`).
3. Android Studio va lancer un **Gradle Sync** automatique. Première fois : 5 à 15 minutes (téléchargement Gradle 8.10, AGP 8.7, plugins Kotlin, dépendances).
4. Si tu vois en bas "Sync finished successfully", c'est bon. Si erreur, retente avec **File → Sync Project with Gradle Files**.

> 💡 Si Android Studio te demande d'installer un SDK ou de mettre à jour AGP, accepte.

---

## 3. Obtenir la clé API Groq (gratuite)

1. Va sur https://console.groq.com/ et inscris-toi (gratuit).
2. Menu **API Keys → Create API Key**. Copie la clé (`gsk_…`).
3. À la **racine** du projet AnimIAMVP, crée un fichier `secrets.properties` :

```properties
GROQ_API_KEY=gsk_x
```

> ⚠️ Ce fichier est dans `.gitignore`, il ne sera pas committé. Ne le partage pas.

Après l'avoir créé : **File → Sync Project with Gradle Files** pour que `BuildConfig.GROQ_API_KEY` soit régénéré.

---

## 4. Télécharger les modèles d'identification (4 modèles TFLite)

L'app exécute **4 modèles MediaPipe en parallèle** et garde la prédiction avec le meilleur score. Tu n'es pas obligé de tous les avoir (l'app marche tant qu'au moins un est présent), mais pour la précision max il faut les 4.

Crée le dossier `app/src/main/assets/` (clic-droit sur `app/src/main` → **New → Directory → assets**), puis dépose les fichiers ci-dessous avec **le nom exact** indiqué.

### A. Oiseaux — `animal_classifier_birds.tflite` (~18 MB, 965 espèces)

1. Page Kaggle : https://www.kaggle.com/models/google/aiy/tfLite/vision-classifier-birds-v1
2. Clique **Download → variation: Float, version: 1**.
3. Renomme le fichier téléchargé en `animal_classifier_birds.tflite`.

### B. Insectes — `animal_classifier_insects.tflite` (~18 MB, 1021 catégories)

1. https://www.kaggle.com/models/google/aiy/tfLite/vision-classifier-insects-v1
2. Download → Float / version 1.
3. Renomme en `animal_classifier_insects.tflite`.

### C. Plantes — `animal_classifier_plants.tflite` (~18 MB, 2102 espèces)

1. https://www.kaggle.com/models/google/aiy/tfLite/vision-classifier-plants-v1
2. Download → Float / version 1.
3. Renomme en `animal_classifier_plants.tflite`.

### D. Généraliste (mammifères + reptiles + poissons + autres) — `animal_classifier_generic.tflite`

EfficientNet Lite 0 entraîné sur ImageNet — couvre ~120 mammifères par race/espèce (chiens, chats, gros félins, primates, ours, éléphants…) et beaucoup d'autres animaux.

1. https://storage.googleapis.com/mediapipe-models/image_classifier/efficientnet_lite0/float32/1/efficientnet_lite0.tflite
2. Renomme en `animal_classifier_generic.tflite`.

### Vérification

Ton dossier `app/src/main/assets/` doit contenir :

```
animal_classifier_birds.tflite
animal_classifier_insects.tflite
animal_classifier_plants.tflite
animal_classifier_generic.tflite
```

> 💡 Si tu n'as qu'un ou deux modèles, l'app fonctionne quand même — elle utilise ce qui est disponible. Mais la précision sera limitée aux règnes que tu as inclus.

### Pourquoi 4 modèles ?

- Chaque modèle AIY est **ultra-précis** dans son domaine (espèce exacte avec nom scientifique)
- L'app les lance **en parallèle** et garde le meilleur score → précision max sans devoir choisir manuellement
- Le nom scientifique extrait est utilisé pour la requête PubMed → articles bien plus pertinents

---

## 5. Lancer sur ton téléphone

### Mode développeur sur Android
1. Sur ton téléphone, **Paramètres → À propos du téléphone**.
2. Appuie 7 fois sur **Numéro de build** → "Vous êtes maintenant développeur".
3. **Paramètres → Système → Options pour les développeurs → Activer le débogage USB**.
4. Branche le téléphone en USB au PC. Accepte le dialogue "Autoriser le débogage USB ?".

### Lancer depuis Android Studio
1. En haut, dans la liste des appareils, ton téléphone doit apparaître. Sélectionne-le.
2. Clique le bouton ▶ vert **Run 'app'**.
3. Première compilation : 1–3 min. L'app s'installe et se lance.

> 💡 Pas de téléphone ? **Tools → Device Manager → Create Device** pour créer un émulateur (Pixel 7 + Android 14 recommandé).

---

## 6. Construire un APK installable

Pour générer un fichier `.apk` que tu peux installer sur n'importe quel téléphone :

1. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
2. Après 1–2 min, une notification "APK(s) generated successfully" s'affiche en bas à droite. Clique **locate**.
3. L'APK est dans `app/build/outputs/apk/debug/app-debug.apk`.
4. Copie-le sur ton téléphone (Bluetooth, USB, Drive…). Ouvre-le pour installer.

> ⚠️ Il faut autoriser "Sources inconnues" la première fois.

---

## 7. Premier test

1. À l'ouverture, accepte les permissions **Caméra** et **Micro**.
2. Appuie sur l'icône **appareil photo** dans la barre du bas → prends un animal en photo.
3. L'app affiche : "Identification en cours…" → "Recherche d'articles…" → réponse de l'IA + liste d'articles PubMed.
4. Tape ou dicte une question de suivi pour continuer la conversation.

---

## Problèmes fréquents

| Erreur | Solution |
|---|---|
| "Clé Groq manquante" | `secrets.properties` non créé ou pas re-syncé. Crée le fichier puis File → Sync Project with Gradle Files. |
| "Aucun modèle d'identification trouvé dans assets/" | Aucun des 4 `animal_classifier_*.tflite` n'est dans `app/src/main/assets/`. Place au moins un fichier en respectant le nom exact. |
| Gradle Sync échoue | Vérifie ta connexion. Réessaie. En dernier recours : File → Invalidate Caches → Restart. |
| L'APK refuse de s'installer | Active "Installer des applis inconnues" pour le navigateur ou gestionnaire de fichiers utilisé. |
| Reconnaissance vocale "indisponible" | Installe Google App + activer "Voix Google" dans les paramètres clavier Android. |
