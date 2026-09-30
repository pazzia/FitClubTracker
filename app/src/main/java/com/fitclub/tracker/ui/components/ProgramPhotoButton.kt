package com.fitclub.tracker.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.fitclub.tracker.data.repo.ProgramPhotos
import com.fitclub.tracker.ui.theme.Fc
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ProgramPhotoButton(photo: String?, onPhoto: (String?) -> Unit, modifier: Modifier = Modifier, onBusy: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnPhoto by rememberUpdatedState(onPhoto)
    val latestOnBusy by rememberUpdatedState(onBusy)
    var menu by rememberSaveable { mutableStateOf(false) }
    var consent by rememberSaveable { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFile by rememberSaveable { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun accept(uri: Uri, captured: File? = null) {
        importing = true
        latestOnBusy(true)
        scope.launch {
            try {
                val name = ProgramPhotos.importPhoto(context, uri)
                latestOnPhoto(name)
            } catch (failure: Exception) {
                error = failure.message ?: "Попробуйте другое изображение"
            } finally {
                captured?.delete()
                importing = false
                latestOnBusy(false)
            }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) accept(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingUri
        val file = pendingFile?.let { File(it) }
        pendingUri = null
        pendingFile = null
        if (success && uri != null) accept(Uri.parse(uri), file) else file?.delete()
    }
    fun launchCamera() {
        try {
            val folder = File(context.cacheDir, "cover_captures").apply { mkdirs() }
            val file = File.createTempFile("capture-", ".jpg", folder)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            pendingUri = uri.toString()
            pendingFile = file.absolutePath
            camera.launch(uri)
        } catch (failure: Exception) {
            pendingFile?.let { File(it).delete() }
            pendingUri = null; pendingFile = null
            error = "Камера недоступна. Можно выбрать фото из галереи."
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else denied = true
    }
    Box(modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(Fc.Surface)
        .border(1.5.dp, if (photo != null) Fc.Lime else Fc.Line, RoundedCornerShape(12.dp))
        .clickable(enabled = !importing, role = Role.Button) { menu = true }, contentAlignment = Alignment.Center) {
        if (importing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Fc.Lime)
        else Icon(Icons.Outlined.PhotoCamera, contentDescription = "Фото обложки", tint = if (photo != null) Fc.Lime else Fc.Muted)
    }
    if (menu) AlertDialog(onDismissRequest = { menu = false }, title = { Text("Обложка программы") },
        text = { Column {
            TextButton(onClick = {
                menu = false
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                else permission.launch(Manifest.permission.CAMERA)
            }) { Text("Сделать фото") }
            TextButton(onClick = { menu = false; consent = true }) { Text("Выбрать из галереи") }
            if (photo != null) TextButton(onClick = { latestOnPhoto(null); menu = false }) { Text("Удалить фото", color = Fc.Pink) }
        } }, confirmButton = { TextButton(onClick = { menu = false }) { Text("Отмена") } })
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text("Разрешить использовать фото?") },
        text = { Text("Вы выберете одно изображение в системной галерее. Оно сохранится как обложка программы.") },
        confirmButton = { TextButton(onClick = {
            consent = false
            try { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            catch (failure: Exception) { error = "Галерея недоступна. Попробуйте сделать фото." }
        }) { Text("Продолжить") } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text("Отмена") } })
    if (denied) AlertDialog(onDismissRequest = { denied = false }, title = { Text("Доступ к камере не разрешён") },
        text = { Text("Можно разрешить доступ в настройках или выбрать изображение из галереи.") },
        confirmButton = { TextButton(onClick = {
            denied = false
            runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                .onFailure { error = "Не удалось открыть настройки" }
        }) { Text("Настройки") } },
        dismissButton = { TextButton(onClick = { denied = false }) { Text("Закрыть") } })
    if (error != null) AlertDialog(onDismissRequest = { error = null }, title = { Text("Не удалось загрузить фото") },
        text = { Text(error.orEmpty()) }, confirmButton = { TextButton(onClick = { error = null }) { Text("Закрыть") } })
}
