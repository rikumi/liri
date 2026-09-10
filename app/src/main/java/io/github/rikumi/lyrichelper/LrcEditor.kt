package io.github.rikumi.lyrichelper

import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.icon.extended.Redo
import top.yukonga.miuix.kmp.icon.extended.Undo
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Replace
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val editorTimeTag = Regex("\\[\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?\\]")
private val editorLeadingTimeTags = Regex("^(?:\\[\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?\\])+")
private val editorOffsetTag = Regex("^\\[offset\\s*:\\s*(-?\\d+)\\]", RegexOption.IGNORE_CASE)
private val editorMonospace = FontFamily(Font(R.font.maple_mono_regular))

@Composable
internal fun LocalLrcEditorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.settingsPrefs()
    val title = prefs.getString("editor_target_title", prefs.getString("now_title", "")) ?: ""
    val artist = prefs.getString("editor_target_artist", prefs.getString("now_artist", "")) ?: ""
    val fileName = "$title - $artist.lrc".replace(Regex("[\\\\/:*?\\\"<>|]"), "_")
    val file = remember(fileName) { File("/sdcard/Music/Liri", fileName) }
    val savedText = remember(file.path) { runCatching { file.readText() }.getOrDefault("") }
    var value by remember(file.path) { mutableStateOf(TextFieldValue(savedText)) }
    var lastSavedText by remember(file.path) { mutableStateOf(savedText) }
    var showSaveDialog by remember { mutableStateOf(false) }
    val isDirty = value.text != lastSavedText
    fun requestExit() { if (isDirty) showSaveDialog = true else onBack() }
    fun saveFile(): Boolean = runCatching {
        file.parentFile?.mkdirs()
        FileOutputStream(file, false).use { it.write(value.text.toByteArray(Charsets.UTF_8)) }
    }.onSuccess {
        lastSavedText = value.text
        Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
        context.startService(Intent(context, MainService::class.java).setAction(ACTION_RELOAD_LOCAL_LYRICS))
    }
        .onFailure { Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_SHORT).show() }
        .isSuccess
    BackHandler { requestExit() }
    val undoStack = remember(file.path) { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember(file.path) { mutableStateListOf<TextFieldValue>() }
    val editorScrollState = rememberScrollState()
    val density = LocalDensity.current
    var editorTextLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var editorTextTopPx by remember { mutableStateOf(0f) }
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    fun applyEdit(next: TextFieldValue) {
        if (next.text != value.text || next.selection != value.selection) {
            undoStack.add(value)
            redoStack.clear()
            value = next
        }
    }
    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(value)
            value = undoStack.removeAt(undoStack.lastIndex)
        }
    }
    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(value)
            value = redoStack.removeAt(redoStack.lastIndex)
        }
    }
    val isCurrentPlayback = prefs.getString("playback_title", "") == title && prefs.getString("playback_artist", "") == artist
    var positionMs by remember { mutableIntStateOf(if (isCurrentPlayback) prefs.getLong("playback_position_ms", 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt() else 0) }
    var durationMs by remember { mutableIntStateOf(if (isCurrentPlayback) prefs.getLong("playback_duration_ms", 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt() else 0) }
    var playing by remember { mutableStateOf(isCurrentPlayback && prefs.getBoolean("playback_is_playing", false)) }
    DisposableEffect(fileName) {
        context.startService(Intent(context, MainService::class.java).apply {
            action = ACTION_EDITOR_START
            putExtra(EXTRA_EDITOR_TITLE, title)
            putExtra(EXTRA_EDITOR_ARTIST, artist)
            putExtra(EXTRA_EDITOR_PACKAGE, prefs.getString("editor_target_package", ""))
        })
        onDispose {
            context.startService(Intent(context, MainService::class.java).setAction(ACTION_EDITOR_STOP))
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val now = context.settingsPrefs()
            val current = now.getString("playback_title", "") == title && now.getString("playback_artist", "") == artist
            if (current) {
                val base = now.getLong("playback_position_ms", 0L)
                val updated = now.getLong("playback_updated_elapsed", 0L)
                positionMs = (base + if (now.getBoolean("playback_is_playing", false)) (SystemClock.elapsedRealtime() - updated) else 0L)
                    .coerceAtLeast(0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                durationMs = now.getLong("playback_duration_ms", 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                playing = now.getBoolean("playback_is_playing", false)
            } else {
                positionMs = 0
                durationMs = 0
                playing = false
            }
            delay(100)
        }
    }
    LaunchedEffect(imeVisible, value.selection.start) {
        if (imeVisible) {
            delay(80)
            val line = value.text.take(value.selection.start.coerceIn(0, value.text.length)).count { it == '\n' }
            val lineHeight = with(density) { 20.dp.roundToPx() }
            editorScrollState.scrollTo((line * lineHeight - lineHeight * 4).coerceAtLeast(0))
        }
    }
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            CouixTopAppBar(
                fileName,
                navigationIcon = { CouixBackButton(::requestExit) },
                actions = {
                    val canUndo = undoStack.isNotEmpty()
                    val canRedo = redoStack.isNotEmpty()
                    top.yukonga.miuix.kmp.basic.IconButton(onClick = ::undo, enabled = canUndo) {
                        top.yukonga.miuix.kmp.basic.Icon(MiuixIcons.Undo, contentDescription = "撤销", tint = MiuixTheme.colorScheme.onSurface.copy(alpha = if (canUndo) 1f else 0.38f), modifier = Modifier.size(22.dp))
                    }
                    top.yukonga.miuix.kmp.basic.IconButton(onClick = ::redo, enabled = canRedo) {
                        top.yukonga.miuix.kmp.basic.Icon(MiuixIcons.Redo, contentDescription = "重做", tint = MiuixTheme.colorScheme.onSurface.copy(alpha = if (canRedo) 1f else 0.38f), modifier = Modifier.size(22.dp))
                    }
                },
            )
        },
        bottomBar = {
            val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
            val barContentColor = if (darkTheme) Color.White else Color(0xFF212121)
            val trackColor = barContentColor.copy(alpha = 0.16f)
            val progressColor = MiuixTheme.colorScheme.primary
            val progressTextColor = barContentColor
            val barColor = if (darkTheme) Color(0xFF151515) else Color(0xFFF0F0F0)
            Column(modifier = Modifier.fillMaxWidth().background(barColor).navigationBarsPadding().imePadding()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f).height(24.dp), contentAlignment = Alignment.CenterStart) {
                        Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                            drawRect(trackColor)
                            if (durationMs > 0) drawRect(progressColor, size = size.copy(width = size.width * (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    BasicText(
                        "%d:%02d / %d:%02d".format(Locale.ROOT, positionMs / 60000, positionMs / 1000 % 60, durationMs / 60000, durationMs / 1000 % 60),
                        style = MiuixTheme.textStyles.body2.copy(fontFamily = editorMonospace, fontFeatureSettings = "kern", letterSpacing = 0.sp, color = progressTextColor),
                    )
                    top.yukonga.miuix.kmp.basic.IconButton(onClick = { context.startService(Intent(context, MainService::class.java).setAction(ACTION_EDITOR_TOGGLE_PLAYBACK)) }) {
                        top.yukonga.miuix.kmp.basic.Icon(if (playing) MiuixIcons.Pause else MiuixIcons.Play, contentDescription = if (playing) "暂停" else "播放", tint = barContentColor, modifier = Modifier.size(22.dp))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    EditorAction("替换标签", { top.yukonga.miuix.kmp.basic.Icon(MiuixIcons.Replace, null, tint = barContentColor, modifier = Modifier.size(22.dp)) }, imeVisible) { applyEdit(editorReplace(value, positionMs)) }
                    EditorAction("添加标签", { top.yukonga.miuix.kmp.basic.Icon(MiuixIcons.Add, null, tint = barContentColor, modifier = Modifier.size(22.dp)) }, imeVisible) { applyEdit(editorInsert(value, positionMs)) }
                    EditorAction("删除标签", { EditorDeleteIcon() }, imeVisible) { applyEdit(editorDelete(value)) }
                    EditorBatchAction(
                        showLabel = !imeVisible,
                        onFormat = { applyEdit(TextFieldValue(editorFormat(value.text, prefs))) },
                        onClearAllTags = { applyEdit(editorClearAllTags(value)) },
                    )
                    EditorAction("保存", { top.yukonga.miuix.kmp.basic.Icon(MiuixIcons.Folder, null, tint = barContentColor, modifier = Modifier.size(22.dp)) }, imeVisible) {
                        runCatching {
                            saveFile()
                        }
                    }
                }
            }
        },
    ) { padding ->
        val accent = MiuixTheme.colorScheme.primary
        val currentLineStart = editorCurrentLineStart(value.text, positionMs)
        Box(modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface).padding(padding)) {
            Canvas(Modifier.fillMaxSize()) {
                currentLineStart?.let { start ->
                    editorTextLayout?.let { layout ->
                        val lineEnd = value.text.indexOf('\n', start).let { if (it < 0) value.text.length else it }
                        val firstLine = layout.getLineForOffset(start.coerceIn(0, value.text.length))
                        val lastOffset = if (lineEnd > start) lineEnd - 1 else start
                        val lastLine = layout.getLineForOffset(lastOffset.coerceIn(0, value.text.length))
                        // editorTextTopPx 位于可滚动内容中，已经包含当前滚动偏移，不能再次扣除。
                        val top = editorTextTopPx + layout.getLineTop(firstLine)
                        val bottom = editorTextTopPx + layout.getLineBottom(lastLine)
                        drawRect(
                            accent.copy(alpha = 0.24f),
                            topLeft = Offset(0f, top),
                            size = androidx.compose.ui.geometry.Size(size.width, (bottom - top).coerceAtLeast(0f)),
                        )
                    }
                }
            }
            BasicTextField(
                value = value,
                onValueChange = { applyEdit(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(editorScrollState)
                    .padding(16.dp)
                    .onGloballyPositioned { editorTextTopPx = it.positionInParent().y },
                textStyle = MiuixTheme.textStyles.body2.copy(fontFamily = editorMonospace, fontFeatureSettings = "kern", letterSpacing = 0.sp, color = MiuixTheme.colorScheme.onSurface, lineHeight = 20.sp),
                visualTransformation = remember(accent, currentLineStart) { EditorLrcVisualTransformation(accent, currentLineStart) },
                cursorBrush = SolidColor(accent),
                onTextLayout = { editorTextLayout = it },
            )
        }
    }
    if (showSaveDialog) {
        CouixConfirmDialog(
            title = "保存歌词",
            text = "歌词已修改，是否保存？",
            confirmLabel = "保存",
            dismissLabel = "不保存",
            onConfirm = {
                showSaveDialog = false
                if (saveFile()) onBack()
            },
            onDismiss = {
                showSaveDialog = false
                onBack()
            },
        )
    }
}

@Composable
private fun EditorAction(description: String, icon: @Composable () -> Unit, hideLabel: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        top.yukonga.miuix.kmp.basic.IconButton(onClick = onClick) {
            Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) { icon() }
        }
        if (!hideLabel) BasicText(description, style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp, color = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color(0xFF212121)))
    }
}

@Composable
private fun EditorReplaceIcon() {
    val color = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color(0xFF212121)
    Canvas(Modifier.size(22.dp)) {
        drawPath(Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.25f)
            lineTo(size.width * 0.88f, size.height * 0.25f)
            lineTo(size.width * 0.5f, size.height * 0.78f)
            close()
        }, color)
    }
}

@Composable
private fun EditorDeleteIcon() {
    val color = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color(0xFF212121)
    Canvas(Modifier.size(22.dp)) {
        drawLine(color, Offset(size.width * 0.16f, size.height / 2), Offset(size.width * 0.84f, size.height / 2), strokeWidth = size.height * 0.12f, cap = StrokeCap.Round)
    }
}

@Composable
private fun EditorBatchAction(showLabel: Boolean, onFormat: () -> Unit, onClearAllTags: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var popupVisible by remember { mutableStateOf(false) }
    var anchorHeightPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(expanded) {
        if (expanded) popupVisible = true else {
            delay(220)
            popupVisible = false
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        top.yukonga.miuix.kmp.basic.IconButton(onClick = { expanded = !expanded }, modifier = Modifier.onGloballyPositioned { anchorHeightPx = it.size.height }) {
            top.yukonga.miuix.kmp.basic.Icon(MiuixIcons.Tune, contentDescription = "格式化", tint = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color(0xFF212121), modifier = Modifier.size(22.dp))
        }
        if (showLabel) BasicText("批量操作", style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp, color = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color(0xFF212121)))
        CouixDropdownPopup(expanded = popupVisible, anchorHeightPx = anchorHeightPx, onDismissRequest = { expanded = false }) {
            CouixDropdownItem("格式化歌词", selected = false, onClick = { expanded = false; onFormat() })
            CouixDropdownDivider()
            CouixDropdownItem("清除所有标签", selected = false, onClick = { expanded = false; onClearAllTags() })
        }
    }
}

private data class EditorLineRange(val start: Int, val endExclusive: Int)

private fun editorLineRange(text: String, cursor: Int): EditorLineRange {
    val start = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)) + 1
    val end = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
    return EditorLineRange(start, end)
}

private fun editorMoveNext(text: String, lineEndExclusive: Int): Int = if (lineEndExclusive < text.length) lineEndExclusive + 1 else text.length

private fun editorTimestamp(positionMs: Int): String {
    val total = positionMs.coerceAtLeast(0)
    return "[%02d:%02d.%02d]".format(Locale.ROOT, total / 60000, total / 1000 % 60, total % 1000 / 10)
}

private fun editorReplace(value: TextFieldValue, positionMs: Int): TextFieldValue {
    val range = editorLineRange(value.text, value.selection.start)
    val line = value.text.substring(range.start, range.endExclusive)
    val replaced = editorTimestamp(positionMs) + editorTimeTag.replace(line, "")
    val text = value.text.replaceRange(range.start, range.endExclusive, replaced)
    return TextFieldValue(text, TextRange(editorMoveNext(text, range.start + replaced.length)))
}

private fun editorInsert(value: TextFieldValue, positionMs: Int): TextFieldValue {
    val range = editorLineRange(value.text, value.selection.start)
    val line = value.text.substring(range.start, range.endExclusive)
    val prefix = editorLeadingTimeTags.find(line)?.value ?: ""
    val replaced = if (prefix.isEmpty()) editorTimestamp(positionMs) + line else prefix + editorTimestamp(positionMs) + line.removePrefix(prefix)
    val text = value.text.replaceRange(range.start, range.endExclusive, replaced)
    return TextFieldValue(text, TextRange(editorMoveNext(text, range.start + replaced.length)))
}

private fun editorDelete(value: TextFieldValue): TextFieldValue {
    val range = editorLineRange(value.text, value.selection.start)
    val replaced = editorTimeTag.replace(value.text.substring(range.start, range.endExclusive), "")
    val text = value.text.replaceRange(range.start, range.endExclusive, replaced)
    return TextFieldValue(text, TextRange(editorMoveNext(text, range.start + replaced.length)))
}

private fun editorClearAllTags(value: TextFieldValue): TextFieldValue =
    TextFieldValue(editorTimeTag.replace(value.text, ""), TextRange(value.selection.start.coerceAtMost(editorTimeTag.replace(value.text, "").length)))

private fun editorFormat(text: String, prefs: android.content.SharedPreferences): String {
    data class Timed(val time: Int, val lyric: String)
    val entries = mutableListOf<Timed>()
    var tagOffset = 0
    text.lineSequence().forEach { line ->
        editorOffsetTag.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { tagOffset = it }
        if (editorTimeTag.find(line) == null) return@forEach
        val lyric = editorTimeTag.replace(line, "").trim()
        editorTimeTag.findAll(line).forEach { match ->
            val parts = match.value.trim('[', ']').split(':', limit = 2)
            val seconds = parts.getOrNull(1)?.replace('.', ':')?.split(':') ?: return@forEach
            val minute = parts.firstOrNull()?.toIntOrNull() ?: return@forEach
            val second = seconds.firstOrNull()?.toIntOrNull() ?: return@forEach
            val fraction = seconds.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toIntOrNull() ?: 0
            entries += Timed((minute * 60_000 + second * 1_000 + fraction + tagOffset + prefs.getInt("lyric_offset_ms", -500)).coerceAtLeast(0), lyric)
        }
    }
    return entries.sortedBy { it.time }.joinToString("\n") { "[%02d:%02d.%02d]%s".format(Locale.ROOT, it.time / 60000, it.time / 1000 % 60, it.time % 1000 / 10, it.lyric) }
}

private fun editorCurrentLineStart(text: String, positionMs: Int): Int? {
    var activeTime = Int.MIN_VALUE
    var activeLineStart: Int? = null
    var lineStart = 0
    text.splitToSequence('\n').forEach { line ->
        editorTimeTag.findAll(line).forEach { match ->
            val raw = match.value.trim('[', ']').split(':', limit = 2)
            val minute = raw.getOrNull(0)?.toIntOrNull() ?: return@forEach
            val seconds = raw.getOrNull(1)?.replace('.', ':')?.split(':') ?: return@forEach
            val second = seconds.getOrNull(0)?.toIntOrNull() ?: return@forEach
            val fraction = seconds.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toIntOrNull() ?: 0
            val time = minute * 60_000 + second * 1_000 + fraction
            if (time <= positionMs && time >= activeTime) {
                activeTime = time
                activeLineStart = lineStart
            }
        }
        lineStart += line.length + 1
    }
    return activeLineStart
}

private class EditorLrcVisualTransformation(
    private val accent: androidx.compose.ui.graphics.Color,
    private val currentLineStart: Int?,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text)
        currentLineStart?.let { start ->
            val end = text.text.indexOf('\n', start).let { if (it < 0) text.text.length else it }
            if (start < end) {
                builder.addStyle(
                    SpanStyle(color = accent, fontWeight = FontWeight.Medium),
                    start,
                    end,
                )
            }
        }
        editorTimeTag.findAll(text.text).forEach { match ->
            builder.addStyle(SpanStyle(color = accent, background = accent.copy(alpha = 0.16f)), match.range.first, match.range.last + 1)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
