package io.github.rikumi.lyrichelper

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.layout.onSizeChanged
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.preference.*
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.window.WindowDialog
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

internal val SETTINGS_CARD_CORNER = 12.dp
internal val SETTINGS_BACK_ICON = 26.dp
private val SETTINGS_GROUP_HMARGIN = 16.dp
private val SETTINGS_CARD_BOTTOM_GAP = 16.dp
private val SETTINGS_ROW_HPADDING = 16.dp
private val SETTINGS_ROW_VPADDING = 12.dp
private val SETTINGS_DIVIDER_INSET = 16.dp
private const val SETTINGS_PUNCTUATION_FONT_FEATURES = "kern,halt"

@Composable
fun SettingsSmallTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    SmallTitle(text = text, modifier = modifier)
}

@Composable
fun SettingsStatusBar() {
    val view = LocalView.current
    val surface = COUITheme.colorScheme.surface
    // 亮底配深色图标, 暗底配浅色图标: 以感知亮度判定, 不依赖主题模式枚举。
    val lightIcons = surface.luminance() > 0.5f
    DisposableEffect(view, surface, lightIcons) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            window.statusBarColor = surface.toArgb()
            WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = lightIcons
        }
        onDispose { }
    }
}

@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val baseCard = CardDefaults.defaultColors()
    val cardColors: CardColors = baseCard.copy(color = backgroundColor ?: baseCard.color)
    Card(
        modifier = modifier.padding(
            start = SETTINGS_GROUP_HMARGIN,
            top = 2.dp,
            end = SETTINGS_GROUP_HMARGIN,
            bottom = SETTINGS_CARD_BOTTOM_GAP,
        ),
        cornerRadius = SETTINGS_CARD_CORNER,
        colors = cardColors,
        content = content,
    )
}

@Composable
internal fun SettingsCategoryRow(
    icon: ImageVector?,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    selected: Boolean? = null,
) {
    ArrowPreference(
        title = title, onClick = onClick,
        modifier = modifier.then(if (selected != null) Modifier.semantics { this.selected = selected } else Modifier),
        holdDownState = selected == true,
        startAction = icon?.let { image -> { Icon(imageVector = image, contentDescription = null, modifier = Modifier.size(22.dp)) } },
        endActions = { if (subtitle != null) Text(text = subtitle, color = COUITheme.colorScheme.onSurfaceVariantSummary) },
    )
}

@Composable
fun SettingsSwitchPreference(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onTitleClick: (() -> Unit)? = null,
    leftTrailingContent: @Composable RowScope.() -> Unit = {},
    showDivider: Boolean = false,
    enabled: Boolean = true,
) {
    if (onTitleClick == null) {
        SwitchPreference(
            checked = checked, onCheckedChange = onCheckedChange, title = title,
            summary = subtitle, summaryColor = BasicComponentDefaults.summaryColor(color = COUITheme.colorScheme.onSurfaceVariantSummary), modifier = modifier, enabled = enabled,
            endActions = leftTrailingContent,
        )
    } else {
        // Expandable settings keep title clicks separate from the switch action.
        BasicComponent(
            title = title, summary = subtitle, summaryColor = BasicComponentDefaults.summaryColor(color = COUITheme.colorScheme.onSurfaceVariantSummary), modifier = modifier, enabled = enabled,
            onClick = onTitleClick,
            endActions = {
                leftTrailingContent()
                if (showDivider) {
                    VerticalDivider(
                        modifier = Modifier.padding(start = 12.dp).height(24.dp),
                        thickness = with(LocalDensity.current) { 1.toDp() },
                        color = COUITheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.2f),
                    )
                }
                Switch(
                    checked = checked, onCheckedChange = onCheckedChange,
                    enabled = enabled, modifier = Modifier.padding(start = 12.dp),
                )
            },
        )
    }
}

@Composable
fun SettingsItemDivider(modifier: Modifier = Modifier, startInset: Dp = SETTINGS_DIVIDER_INSET) {
    HorizontalDivider(
        modifier = modifier.padding(start = startInset, end = 16.dp),
    )
}

@Composable
fun SettingsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value, onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished, modifier = modifier,
    )
}

@Composable
internal fun SettingsPreferenceText(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    titleColor: Color = COUITheme.colorScheme.onSurface,
    subtitleColor: Color = COUITheme.colorScheme.onSurfaceVariantSummary,
    animateText: Boolean = false,
    progress: Float? = null,
    contentHorizontalPadding: Dp = 0.dp,
    animateProgress: Boolean = true,
    compressPunctuation: Boolean = false,
    textAlign: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Start,
) {
    val progressColor = COUITheme.colorScheme.primary.copy(alpha = 0.16f)
    val targetProgress = progress?.coerceIn(0f, 1f) ?: 0f
    var previousProgress by remember { mutableFloatStateOf(targetProgress) }
    val progressDecreased = targetProgress < previousProgress
    LaunchedEffect(targetProgress) {
        previousProgress = targetProgress
    }
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = if (!animateProgress || targetProgress == 0f || progressDecreased) snap() else tween(650, easing = LinearEasing),
        label = "liri_lyric_progress",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SETTINGS_ROW_VPADDING),
        verticalArrangement = Arrangement.Bottom,
    ) {
        if (animateText) {
            val lyricLineStyle = COUITheme.textStyles.body1.copy(
                color = titleColor,
                lineHeight = 26.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = true),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None,
                ),
                textAlign = textAlign,
                fontFeatureSettings = if (compressPunctuation) SETTINGS_PUNCTUATION_FONT_FEATURES else null,
            )
            val lyricSubtitleStyle = lyricLineStyle.copy(color = subtitleColor)
            AnimatedContent(
                targetState = title to subtitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredHeight(52.dp)
                    .drawBehind {
                        if (progress != null) {
                            drawRect(
                                color = progressColor,
                                size = Size(
                                    width = size.width * animatedProgress,
                                    height = 26.dp.toPx(),
                                ),
                            )
                        }
                    },
                contentAlignment = Alignment.BottomCenter,
                transitionSpec = {
                    (slideInVertically(tween(260)) { height -> height } + fadeIn(tween(260)))
                        .togetherWith(slideOutVertically(tween(260)) { height -> -height } + fadeOut(tween(260)))
                },
                label = "liri_lyric_lines",
            ) { (current, next) ->
                Column(modifier = Modifier.fillMaxWidth().requiredHeight(52.dp), verticalArrangement = Arrangement.Bottom) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .requiredHeight(26.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        SettingsSinglePassMarqueeText(
                            text = current,
                            style = lyricLineStyle,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalPadding = contentHorizontalPadding,
                            textAlign = textAlign,
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth().requiredHeight(26.dp)) {
                        BasicText(
                            text = next,
                            style = lyricSubtitleStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .requiredHeight(26.dp)
                                .padding(horizontal = contentHorizontalPadding),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            BasicText(
                text = title,
                style = COUITheme.textStyles.body1.copy(color = titleColor, textAlign = textAlign, fontFeatureSettings = if (compressPunctuation) SETTINGS_PUNCTUATION_FONT_FEATURES else null),
                modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = subtitle,
                style = COUITheme.textStyles.body2.copy(color = subtitleColor, textAlign = textAlign, fontFeatureSettings = if (compressPunctuation) SETTINGS_PUNCTUATION_FONT_FEATURES else null),
                modifier = Modifier.fillMaxWidth().heightIn(min = 16.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsSinglePassMarqueeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 0.dp,
    textAlign: TextAlign = TextAlign.Start,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textWidthPx = remember(text, style) {
        textMeasurer.measure(
            text = androidx.compose.ui.text.AnnotatedString(text),
            style = style,
            maxLines = 1,
            softWrap = false,
        ).size.width
    }
    var containerWidthPx by remember { mutableIntStateOf(0) }
    val offset = remember { Animatable(0f) }
    val visibleWidthPx = (containerWidthPx - with(density) { horizontalPadding.toPx() * 2 }).coerceAtLeast(0f)
    LaunchedEffect(text, textWidthPx, containerWidthPx, horizontalPadding, textAlign) {
        offset.stop()
        val overflowTarget = (visibleWidthPx - textWidthPx).coerceAtMost(0f)
        // 超长文本必须从内容区域左边开始，不能从居中位置进入滚动。
        val start = 0f
        offset.snapTo(start)
        if (overflowTarget < 0f) {
            delay(500L)
            val asciiRatio = if (text.isEmpty()) 0f else text.count { it.code in 32..126 }.toFloat() / text.length
            val speedPxPerMs = 0.1f * (1f + asciiRatio)
            val duration = (kotlin.math.abs(overflowTarget - start) / speedPxPerMs).toLong().coerceAtLeast(1L)
            offset.animateTo(overflowTarget, animationSpec = tween(durationMillis = duration.toInt(), easing = LinearEasing))
        }
    }
    Box(
        modifier = modifier
            .requiredHeight(26.dp)
            .clipToBounds()
            .onSizeChanged { containerWidthPx = it.width },
    ) {
        val textModifier = if (textWidthPx > visibleWidthPx) {
            Modifier.requiredWidth(
                with(density) { textWidthPx.toDp() } + horizontalPadding * 2,
            )
        } else {
            Modifier.fillMaxWidth()
        }
        BasicText(
            text = text,
            style = style.copy(textAlign = if (textWidthPx > visibleWidthPx) TextAlign.Start else textAlign),
            modifier = textModifier
                .padding(horizontal = horizontalPadding)
                .offset { IntOffset(offset.value.roundToInt(), 0) },
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
internal fun SettingsActionPairRow(
    leftTitle: String,
    onLeftClick: () -> Unit,
    rightTitle: String,
    onRightClick: () -> Unit,
    leftIcon: ImageVector? = null,
    rightIcon: ImageVector? = null,
    leftShowChevron: Boolean = true,
    rightShowChevron: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val dividerWidth: Dp = (1f / density.density).dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsActionCell(
            title = leftTitle,
            icon = leftIcon,
            showChevron = leftShowChevron,
            onClick = onLeftClick,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = SETTINGS_ROW_VPADDING)
                .width(dividerWidth)
                .background(COUITheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.2f)),
        )
        SettingsActionCell(
            title = rightTitle,
            icon = rightIcon,
            showChevron = rightShowChevron,
            onClick = onRightClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun SettingsGroup(
    items: List<SwitchItem>,
    prefs: SharedPreferences,
    ctx: Context,
    version: Int = 0,
    overrideValue: Boolean? = null,
    onItemChanged: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    SettingsCard(modifier = modifier) {
        items.forEachIndexed { index, item ->
            if (index > 0) SettingsItemDivider()
            key(item.key) {
                SettingsSwitchRow(item = item, prefs = prefs, ctx = ctx, version = version,
                    overrideValue = overrideValue, onItemChanged = onItemChanged)
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    item: SwitchItem,
    prefs: SharedPreferences,
    ctx: Context,
    version: Int,
    overrideValue: Boolean?,
    onItemChanged: () -> Unit,
) {
    // overrideValue 非空时优先显示覆盖值(主开关动画期间), 否则读 prefs;
    // version/overrideValue 变化时重新计算, 其余时刻用本地状态即时切换。
    var checked by remember(item.key, version, overrideValue) {
        mutableStateOf(overrideValue ?: prefs.getBoolean(item.key, false))
    }
    if (item.sliderKey == null) {
        SettingsSwitchPreference(
            checked = checked,
            onCheckedChange = {
                checked = it
                setBool(ctx, item.key, it)
                onItemChanged()
            },
            title = item.label,
            subtitle = item.subtitle,
        )
        return
    }
    // 带滑条的设置项: 数值显示在标题行右侧, 滑条默认折叠; 单独开启功能时自动展开。
    var expanded by remember(item.key) { mutableStateOf(false) }
    var intVal by remember(item.sliderKey, item.sliderMin, item.sliderMax, version, overrideValue) {
        mutableStateOf(snapSliderValue(item, prefs.getInt(item.sliderKey, item.sliderDefault)))
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSwitchPreference(
            checked = checked,
            onCheckedChange = {
                checked = it
                expanded = it
                setBool(ctx, item.key, it)
                if (!it) {
                    intVal = item.sliderDefault
                    setInt(ctx, item.sliderKey, item.sliderDefault)
                }
                onItemChanged()
            },
            title = item.label,
            subtitle = item.subtitle,
            onTitleClick = if (checked) {
                { expanded = !expanded }
            } else {
                null
            },
            leftTrailingContent = {
                if (checked) {
                    BasicText(
                        text = item.sliderValueText?.invoke(intVal) ?: "${intVal}${item.sliderUnit}",
                        style = COUITheme.textStyles.body2.copy(
                            color = COUITheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            fontWeight = if (intVal != item.sliderDefault) FontWeight.Medium else null,
                        ),
                        modifier = Modifier
                            .padding(start = 12.dp),
                    )
                }
            },
            showDivider = checked,
        )
        AnimatedVisibility(
            visible = checked && expanded,
            enter = expandVertically(expandFrom = Alignment.Bottom),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        bottom = SETTINGS_ROW_VPADDING,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsSlider(
                    value = ((intVal - item.sliderMin).toFloat() /
                            (item.sliderMax - item.sliderMin).coerceAtLeast(1)).coerceIn(0f, 1f),
                    onValueChange = { f ->
                        val nv = snapSliderValue(
                            item,
                            (item.sliderMin + f * (item.sliderMax - item.sliderMin)).roundToInt(),
                        )
                        if (nv != intVal) {
                            intVal = nv
                            setInt(ctx, item.sliderKey, nv)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

private fun snapSliderValue(item: SwitchItem, raw: Int): Int {
    val step = item.sliderStep.coerceAtLeast(1)
    return (item.sliderMin + ((raw - item.sliderMin + step / 2) / step) * step)
        .coerceIn(item.sliderMin, item.sliderMax)
}

@Composable
private fun SettingsActionCell(title: String, icon: ImageVector?, showChevron: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val start: (@Composable () -> Unit)? = icon?.let { image ->
        { Icon(image, null, modifier = Modifier.size(22.dp)) }
    }
    if (showChevron) {
        ArrowPreference(title = title, onClick = onClick, modifier = modifier, startAction = start)
    } else {
        BasicComponent(title = title, onClick = onClick, modifier = modifier, startAction = start,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
fun SettingsTopAppBar(title: String, modifier: Modifier = Modifier,
    scrollBehavior: ScrollBehavior? = null, navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    SmallTopAppBar(title = title, modifier = modifier, scrollBehavior = scrollBehavior,
        navigationIcon = navigationIcon, actions = actions)
}

@Composable
fun SettingsLargeTitle(title: String, modifier: Modifier = Modifier,
    scrollBehavior: ScrollBehavior? = null, actions: @Composable RowScope.() -> Unit = {},
) {
    SmallTopAppBar(title = title, modifier = modifier, scrollBehavior = scrollBehavior,
        titlePadding = TopAppBarDefaults.TitlePadding + 4.dp, actions = actions)
}

@Composable
fun SettingsConfirmDialog(title: String = "提示", text: String, onConfirm: () -> Unit,
    onDismiss: () -> Unit, confirmLabel: String = "确认", dismissLabel: String = "取消",
) {
    var visible by remember { mutableStateOf(true) }
    var confirmed by remember { mutableStateOf(false) }
    WindowDialog(show = visible, title = title, summary = text,
        onDismissRequest = { visible = false },
        onDismissFinished = { if (confirmed) onConfirm() else onDismiss() },
    ) {
        DialogButtonBar(
            negative = DialogButtonBarAction(text = dismissLabel, enabled = visible, onClick = { visible = false }),
            positive = DialogButtonBarAction(text = confirmLabel, enabled = visible, onClick = { confirmed = true; visible = false }),
        )
    }
}
