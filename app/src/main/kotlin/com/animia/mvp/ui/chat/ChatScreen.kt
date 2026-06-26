package com.animia.mvp.ui.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.animia.mvp.audio.SpeechRecognizerHelper
import com.animia.mvp.ui.chat.components.AnimalAvatar
import com.animia.mvp.ui.chat.components.ArticleCard
import com.animia.mvp.ui.chat.components.Mascot
import com.animia.mvp.ui.chat.components.MessageBubble
import com.animia.mvp.ui.chat.components.StatusIndicator
import com.animia.mvp.ui.theme.GreenAccent
import com.animia.mvp.ui.theme.GreenMist
import com.animia.mvp.ui.theme.GreenPrimary
import com.animia.mvp.ui.theme.GreenSurface
import com.animia.mvp.ui.theme.TextMuted
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    // URI temporaire où la caméra écrit la photo pleine résolution
    var pendingPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) pendingPhotoUri?.let { viewModel.onImagePicked(it) }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: android.net.Uri? ->
        if (uri != null) viewModel.onImagePicked(uri)
    }

    val speechHelper = remember {
        SpeechRecognizerHelper(
            context = context,
            onResult = viewModel::onVoiceTranscript,
            onPartial = viewModel::updatePartialVoice,
            onError = viewModel::reportError,
            onListeningChange = viewModel::setListening
        )
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.clearError()
            }
        }
    }

    Scaffold(
        containerColor = GreenSurface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopHeader(onReset = viewModel::resetConversation) },
        bottomBar = {
            Column {
                InputBar(
                    onSend = viewModel::sendUserText,
                    onCamera = {
                        if (cameraPermission.status.isGranted) {
                            val uri = createImageUri(context)
                            pendingPhotoUri = uri
                            takePictureLauncher.launch(uri)
                        } else {
                            cameraPermission.launchPermissionRequest()
                        }
                    },
                    onGallery = {
                        pickImageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onVoiceToggle = {
                        if (state.status == Status.LISTENING) {
                            speechHelper.stop()
                        } else if (micPermission.status.isGranted) {
                            speechHelper.start()
                        } else {
                            micPermission.launchPermissionRequest()
                        }
                    },
                    isListening = state.status == Status.LISTENING,
                    onSoundRecord = {
                        if (micPermission.status.isGranted) {
                            viewModel.recordAnimalSound()
                        } else {
                            micPermission.launchPermissionRequest()
                        }
                    },
                    isRecording = state.status == Status.RECORDING
                )
                BottomNav(onReset = viewModel::resetConversation)
            }
        }
    ) { padding ->
        if (state.hasConversation) {
            ConversationContent(
                state = state,
                padding = padding,
                onToggleDetails = viewModel::toggleLastAnswerDetails
            )
        } else {
            HomeContent(padding = padding, status = state.status)
        }
    }
}

/** Crée une URI FileProvider dans le cache où la caméra écrira la photo pleine résolution. */
private fun createImageUri(context: android.content.Context): android.net.Uri {
    val file = File.createTempFile("capture_", ".jpg", context.cacheDir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
private fun TopHeader(onReset: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GreenSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "AnimIA",
            color = GreenPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onReset) {
            Icon(Icons.Outlined.RestartAlt, contentDescription = "Nouvelle conversation", tint = GreenPrimary)
        }
    }
}

@Composable
private fun HomeContent(padding: PaddingValues, status: Status) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Mascot(size = 200.dp)
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Salut ! Pose-moi une question sur un animal,",
            color = GreenPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "ou prends-le en photo pour que je l'identifie.",
            color = TextMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        StatusIndicator(status = status)
    }
}

@Composable
private fun ConversationContent(
    state: ChatUiState,
    padding: PaddingValues,
    onToggleDetails: () -> Unit
) {
    val listState = rememberLazyListState()
    // Reset scroll en haut à chaque nouvelle question/réponse
    LaunchedEffect(state.messages.lastOrNull()?.id) {
        listState.animateScrollToItem(0)
    }

    val lastUser = state.messages.lastOrNull { it.role == ChatRole.USER }
    val lastAssistant = state.messages.lastOrNull { it.role == ChatRole.ASSISTANT }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Header : photo de l'animal (ou mascotte si pas d'image) + nom
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AnimalAvatar(imageUrl = state.animalImageUrl, size = 80.dp)
                Column {
                    state.currentAnimal?.let { name ->
                        Text(
                            text = name,
                            color = GreenPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                    state.currentScientificName?.let { sci ->
                        Text(
                            text = sci,
                            color = TextMuted,
                            fontSize = 13.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }
        }

        // 2. Dernière question utilisateur
        lastUser?.let { user ->
            item { MessageBubble(message = user) }
        }

        // 3. Indicateur de statut OU dernière réponse IA
        if (state.status != Status.IDLE) {
            item { StatusIndicator(status = state.status) }
        } else {
            lastAssistant?.let { assistant ->
                val isExpanded = state.expandedMessageIds.contains(assistant.id)
                val display = if (isExpanded) assistant.expandedContent ?: assistant.content
                              else assistant.content
                item { MessageBubble(message = assistant.copy(content = display)) }
                item {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onToggleDetails,
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            GreenPrimary
                        ),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = GreenPrimary
                        )
                    ) {
                        Text(
                            if (isExpanded) "Voir moins" else "Voir plus de détails",
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // 4. "Une autre question ?"
        if (lastAssistant != null && state.status == Status.IDLE) {
            item {
                Text(
                    text = "Une autre question ?",
                    color = GreenPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }
        }

        // 5. Header de la liste d'articles + cartes
        if (state.articles.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${state.articles.size} articles",
                    fontWeight = FontWeight.Bold,
                    color = GreenPrimary,
                    fontSize = 18.sp
                )
            }
            items(state.articles, key = { it.pmid }) { article ->
                ArticleCard(article = article, imageUrl = state.animalImageUrl)
            }
        }
    }
}

@Composable
private fun InputBar(
    onSend: (String) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onVoiceToggle: () -> Unit,
    isListening: Boolean,
    onSoundRecord: () -> Unit,
    isRecording: Boolean
) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GreenSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = onCamera,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = "Photo", tint = GreenPrimary)
        }
        IconButton(
            onClick = onGallery,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            Icon(Icons.Filled.Image, contentDescription = "Galerie", tint = GreenPrimary)
        }
        IconButton(
            onClick = onSoundRecord,
            enabled = !isRecording,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (isRecording) GreenAccent else Color.White)
        ) {
            Icon(
                Icons.Filled.Pets,
                contentDescription = "Reconnaître un cri d'animal",
                tint = if (isRecording) Color.White else GreenPrimary
            )
        }
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Poser une question") },
            singleLine = false,
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (text.isNotBlank()) {
                    onSend(text)
                    text = ""
                }
            })
        )
        IconButton(
            onClick = onVoiceToggle,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (isListening) GreenAccent else Color.White)
        ) {
            Icon(
                if (isListening) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = "Voix",
                tint = if (isListening) Color.White else GreenPrimary
            )
        }
        AnimatedVisibility(visible = text.isNotBlank()) {
            IconButton(
                onClick = {
                    onSend(text)
                    text = ""
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(GreenPrimary)
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Envoyer", tint = Color.White)
            }
        }
    }
}

@Composable
private fun BottomNav(onReset: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GreenPrimary)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Home, contentDescription = "Accueil", tint = Color.White)
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(GreenMist),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onReset) {
                Icon(Icons.Outlined.Public, contentDescription = "Nouveau scan", tint = GreenPrimary)
            }
        }
        Icon(Icons.Outlined.Settings, contentDescription = "Réglages", tint = Color.White)
    }
}

