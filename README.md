# AnimIA MVP

Application Android (Kotlin) — "Shazam des animaux" : prends un animal en photo (ou pose une question), l'IA t'explique et te donne les articles scientifiques associés.

## Stack

- **UI** : Kotlin + Jetpack Compose (Material 3)
- **Vision (local, gratuit)** : MediaPipe Tasks ImageClassifier — 4 modèles TFLite exécutés en parallèle (AIY Birds/Insects/Plants + EfficientNet ImageNet généraliste), on garde la prédiction au meilleur score
- **Voix (local, gratuit)** : Android `SpeechRecognizer` natif (fr-FR)
- **Chat IA** : Groq API (Llama 3.3 70B, tier gratuit)
- **Articles scientifiques** : PubMed E-utilities (gratuit, sans clé)
- **Caméra** : `ActivityResultContracts.TakePicturePreview`

## Démarrage rapide

Voir [INSTALLATION.md](INSTALLATION.md) pour le guide complet (débutant Android Studio).

En résumé :
1. Cloner / ouvrir le projet dans Android Studio.
2. Créer `secrets.properties` à la racine avec `GROQ_API_KEY=…`.
3. Placer 1 à 4 fichiers `animal_classifier_*.tflite` dans `app/src/main/assets/` (voir INSTALLATION.md).
4. ▶ Run.

## Architecture

```
app/src/main/kotlin/com/animia/mvp/
├── MainActivity.kt              Entrée — Compose setContent
├── data/
│   ├── AnimIARepository.kt      Orchestration Groq + PubMed + prompts système
│   ├── groq/                    Client Retrofit Groq (Llama 3.3)
│   └── pubmed/                  Client esearch / esummary / efetch
├── ml/
│   └── AnimalClassifier.kt      MediaPipe ImageClassifier (TFLite)
├── audio/
│   └── SpeechRecognizerHelper.kt  Reconnaissance vocale native
└── ui/
    ├── theme/                   Couleurs vertes, thème Material 3
    └── chat/
        ├── ChatViewModel.kt     StateFlow + pipeline complet
        ├── ChatScreen.kt        UI Compose
        └── components/          Mascotte, bulles, cartes d'articles
```

## Pipeline d'une question

```
[Photo] ──► 4 modèles TFLite en parallèle (coroutines)
              ├─ AIY Birds V1   (965 esp.)
              ├─ AIY Insects V1 (1021 cat.)
              ├─ AIY Plants V1  (2102 esp.)
              └─ EfficientNet ImageNet (généraliste)
                            │ max confidence
                            ▼
              "Pyxicephalus adspersus" (nom scientifique extrait)
                            │
                            ▼
                PubMed esearch + esummary + efetch
                            │
                            ▼
[Texte/Voix] ──► ChatViewModel ──► Groq Llama 3.3
                                   (system prompt + abstracts + historique)
                            │
                            ▼
                Réponse + cartes d'articles
```

## Personnaliser les modèles d'identification

Le code charge tous les fichiers présents parmi :
- `assets/animal_classifier_birds.tflite`
- `assets/animal_classifier_insects.tflite`
- `assets/animal_classifier_plants.tflite`
- `assets/animal_classifier_generic.tflite`

Pour ajouter un nouveau modèle (ex. mammifères custom), ajoute son `ModelSpec` dans `AnimalClassifier.kt` :

```kotlin
val MAMMALS = ModelSpec("mammals", "animal_classifier_mammals.tflite")
val ALL = listOf(BIRDS, INSECTS, PLANTS, GENERIC, MAMMALS)
```

Tu peux entraîner tes propres modèles avec **MediaPipe Model Maker** (Colab gratuit, dataset iNaturalist 2021 mammifères par exemple).

## Personnaliser le modèle LLM

Dans `GroqClient.kt`, change `DEFAULT_MODEL`. Modèles Groq utiles :
- `llama-3.3-70b-versatile` (défaut, qualité max)
- `llama-3.1-8b-instant` (rapide, moins précis)
- `mixtral-8x7b-32768` (long contexte)

## Limitations connues (MVP)

- La clé API Groq est embarquée dans l'APK via `BuildConfig` — OK pour MVP perso, **à mettre derrière un backend** pour un déploiement public.
- `TakePicturePreview` renvoie une thumbnail (~512px) — suffisant pour la classification mais pas pour conserver une vraie photo.
- Pas d'historique persisté entre sessions.
- Pas de fallback offline si pas de connexion (Groq + PubMed requièrent internet).
