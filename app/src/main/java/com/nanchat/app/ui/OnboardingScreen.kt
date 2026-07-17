package com.nanchat.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nanchat.app.R
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import androidx.wear.compose.material3.OutlinedIconButton
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SegmentedCircularProgressIndicator
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import com.nanchat.app.crypto.Bip39
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private sealed interface Step {
    data object Intro : Step
    data class Import(val words: List<String>, val index: Int) : Step
    data class CreateBackup(val seed: String, val mnemonic: List<String>) : Step
    data class CreateName(val seed: String) : Step
}

private data class IntroSlide(
    val icon: ImageVector?,
    val title: String,
    val description: String,
    // Only the first slide uses the real app logo instead of a generic Material icon.
    val useAppLogo: Boolean = false,
)

private val introSlides = listOf(
    IntroSlide(
        icon = null,
        title = "NanChat",
        description = "Fast & private messaging",
        useAppLogo = true,
    ),
    IntroSlide(
        icon = Icons.Outlined.Lock,
        title = "Encrypted",
        description = "Only you can read your chats",
    ),
    IntroSlide(
        icon = Icons.Outlined.Bolt,
        title = "Your identity",
        description = "No accounts - just your key",
    ),
)

/** Flat dark background per slide (Wear OS is dark-theme-only, built on a black background - see
 * the color style guide) instead of the light pastel look a phone-style setup carousel would use. */
@Composable
private fun slideBackgroundColor(index: Int): Color = when (index) {
    0 -> MaterialTheme.colorScheme.surfaceContainerLow
    1 -> MaterialTheme.colorScheme.surfaceContainer
    else -> MaterialTheme.colorScheme.primaryContainer
}

@Composable
fun OnboardingScreen(
    error: String?,
    onImportMnemonic: (List<String>) -> Unit,
    onCreateAccount: (seed: String, name: String) -> Unit,
    onDismissError: () -> Unit,
) {
    var step by remember { mutableStateOf<Step>(Step.Intro) }

    when (val s = step) {
        is Step.Intro -> IntroScreen(
            onCreateNew = {
                val seed = Bip39.generateSeed()
                step = Step.CreateBackup(seed, Bip39.seedToMnemonic(seed))
            },
            onImportExisting = { step = Step.Import(List(Bip39.WORD_COUNT) { "" }, 0) },
            error = error,
        )
        is Step.Import -> WordEntryScreen(
            index = s.index,
            initialValue = s.words[s.index],
            error = error,
            onDismissError = onDismissError,
            onBack = {
                if (s.index == 0) step = Step.Intro
                else step = s.copy(index = s.index - 1)
            },
            onNext = { word ->
                val words = s.words.toMutableList().apply { this[s.index] = word }
                if (s.index == Bip39.WORD_COUNT - 1) {
                    onImportMnemonic(words)
                    step = s.copy(words = words)
                } else {
                    step = Step.Import(words, s.index + 1)
                }
            },
        )
        is Step.CreateBackup -> BackupSeedScreen(
            mnemonic = s.mnemonic,
            onBack = { step = Step.Intro },
            onNext = { step = Step.CreateName(s.seed) },
        )
        is Step.CreateName -> CreateNameScreen(
            error = error,
            onDismissError = onDismissError,
            onBack = { step = Step.CreateBackup(s.seed, Bip39.seedToMnemonic(s.seed)) },
            onConfirm = { name -> onCreateAccount(s.seed, name) },
        )
    }
}

/** First-launch presentation carousel - swipe through what NanChat is, then go straight into the
 * seed import flow (no separate "Import" choice screen).
 *
 * Visual style follows the Pixel Watch "Google style" setup pattern: a full-bleed photo
 * background per slide, a white icon bubble, bold title, a subtle bottom progress arc, and a
 * pill-shaped white CTA button on the final slide. */
@Composable
private fun IntroScreen(onCreateNew: () -> Unit, onImportExisting: () -> Unit, error: String?) {
    val pagerState = rememberPagerState(pageCount = { introSlides.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == introSlides.lastIndex
    val spatialSpec = MotionScheme.expressive().defaultSpatialSpec<Float>()

    // Fade + rise the whole carousel in on first appearance, Google-style entrance motion.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, MotionScheme.expressive().slowSpatialSpec()) }

    AppScaffold {
        ScreenScaffold { contentPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                // Full-bleed flat dark background per slide, crossfading as you swipe.
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(slideBackgroundColor(page)),
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .graphicsLayer {
                            alpha = entrance.value
                            translationY = (1f - entrance.value) * 24f
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                        val slide = introSlides[page]
                        // Content scales/fades with swipe progress instead of snapping between pages.
                        val pageFraction = 1f - pagerState.getOffsetDistanceInPages(page).absoluteValue.coerceIn(0f, 1f)
                        // A Column with fillMaxSize()+Arrangement.Center would constrain each child to
                        // the remaining space and silently truncate the last one - center a
                        // wrap-content Column inside a Box instead so text always gets its full height.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .graphicsLayer {
                                    alpha = pageFraction
                                    val scale = 0.72f + 0.28f * pageFraction
                                    scaleX = scale
                                    scaleY = scale
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                // Icon bubble. The app-logo slide keeps a white badge (the logo art
                                // bakes in its own brand-blue shape and reads correctly on white);
                                // the generic Material icon slides use a tonal container instead,
                                // matching the surfaceContainerHigh circles used elsewhere (e.g.
                                // ChatAvatar).
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (slide.useAppLogo) Color.White
                                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (slide.useAppLogo) {
                                        Image(
                                            painter = painterResource(R.drawable.ic_launcher_foreground),
                                            contentDescription = null,
                                            modifier = Modifier.size(23.dp),
                                        )
                                    } else {
                                        Icon(
                                            imageVector = slide.icon!!,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = slide.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = slide.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    AnimatedContent(
                        targetState = isLastPage,
                        transitionSpec = {
                            (fadeIn(animationSpec = MotionScheme.expressive().defaultEffectsSpec()) +
                                scaleIn(initialScale = 0.85f, animationSpec = spatialSpec))
                                .togetherWith(fadeOut(animationSpec = MotionScheme.expressive().defaultEffectsSpec()))
                        },
                        label = "introAction",
                    ) { showGetStarted ->
                        if (showGetStarted) {
                            // Pill CTA with a leading arrow, using the theme's primary/on-primary
                            // pairing (Button's own defaults) for high contrast on the dark slide bg.
                            // A new identity is just a locally generated key (per the "No accounts -
                            // just your key" slide) - "Create account" is the primary path, with
                            // restoring an existing phrase as a secondary text action underneath.
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                            ) {
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = onCreateNew,
                                    shape = CircleShape,
                                ) {
                                    Text(
                                        "Create account",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                TextButton(onClick = onImportExisting) {
                                    Text(
                                        "Restore",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        } else {
                            TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                                Text("Next", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (error != null) {
                        Spacer(Modifier.height(6.dp))
                        ErrorCard(error, modifier = Modifier.padding(horizontal = 14.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // A bold progress arc hugging the bottom of the bezel tracks progress through the
                // slides, filling left-to-right - the same "step X of N" pattern Wear OS/Pixel
                // Watch setup uses. Mirrored horizontally because the indicator always fills
                // starting at [startAngle], which sits on the right side of the arc otherwise.
                // Hidden on the last slide, where the "Get started" button takes its place.
                val arcAlpha by animateFloatAsState(
                    targetValue = if (isLastPage) 0f else 1f,
                    animationSpec = MotionScheme.expressive().defaultEffectsSpec(),
                    label = "arcAlpha",
                )
                SegmentedCircularProgressIndicator(
                    segmentCount = introSlides.size,
                    progress = { (pagerState.currentPage + 1) / introSlides.size.toFloat() },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = -1f
                            alpha = arcAlpha
                        },
                    startAngle = 25f,
                    endAngle = 155f,
                    strokeWidth = 7.dp,
                    colors = ProgressIndicatorDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun WordEntryScreen(
    index: Int,
    initialValue: String,
    error: String?,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    onNext: (String) -> Unit,
) {
    var input by remember(index) { mutableStateOf(TextFieldValue(initialValue)) }
    val suggestions = remember(input.text) { Bip39.suggestions(input.text) }
    val isValid = Bip39.isKnownWord(input.text)

    val scrollState = rememberScrollState()
    AppScaffold {
        ScreenScaffold(scrollState = scrollState) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .padding(horizontal = 14.dp)
                    .verticalScroll(scrollState),
            ) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Word ${index + 1} of ${Bip39.WORD_COUNT}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.titleMedium.fontSize,
                        textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                )
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(suggestions) { suggestion ->
                            FilledTonalButton(onClick = { onNext(suggestion) }) {
                                Text(suggestion)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { onNext(input.text) },
                        enabled = isValid,
                        icon = if (index == Bip39.WORD_COUNT - 1) {
                            { Icon(Icons.Outlined.Check, contentDescription = null) }
                        } else null,
                    ) {
                        Text(if (index == Bip39.WORD_COUNT - 1) "Confirm" else "Next")
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    ErrorCard(error, onRetry = onDismissError)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Shows the freshly generated 24-word recovery phrase before it's ever saved anywhere, "tap to
 * reveal" like the phone app's MnemonicWords component (old/NanChat/src/components/Initialize/
 * create/Mnemonic.tsx) - hidden by default so it isn't shoulder-surfed by accident while setting up. */
@Composable
private fun BackupSeedScreen(mnemonic: List<String>, onBack: () -> Unit, onNext: () -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    AppScaffold {
        ScreenScaffold(scrollState = scrollState) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .padding(horizontal = 14.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Your recovery phrase",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Write it down. It's the only way to recover your account - never share it.",
                    style = MaterialTheme.typography.bodyExtraSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                MnemonicGrid(mnemonic = mnemonic, revealed = revealed, onToggle = { revealed = !revealed })
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Button(modifier = Modifier.weight(1f), onClick = onNext) {
                        Text("Next", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Numbered word list shared by [BackupSeedScreen] and the Settings "show recovery phrase" screen -
 * one word per row (not a 2-column grid): even the smallest round Wear screens aren't wide enough to
 * fit two boxes side by side without ellipsizing longer wordlist entries. */
@Composable
internal fun MnemonicGrid(mnemonic: List<String>, revealed: Boolean, onToggle: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        mnemonic.forEachIndexed { index, word ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (revealed) word else "••••••",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(2.dp))
        FilledTonalButton(onClick = onToggle) {
            Text(if (revealed) "Hide" else "Tap to reveal")
        }
    }
}

@Composable
private fun CreateNameScreen(
    error: String?,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by remember { mutableStateOf(TextFieldValue("")) }
    val isValid = input.text.trim().isNotEmpty() && input.text.trim().length <= 24
    val scrollState = rememberScrollState()

    AppScaffold {
        ScreenScaffold(scrollState = scrollState) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .padding(horizontal = 14.dp)
                    .verticalScroll(scrollState),
            ) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Choose a name",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.titleMedium.fontSize,
                        textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { onConfirm(input.text.trim()) },
                        enabled = isValid,
                    ) {
                        Text("Create", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    ErrorCard(error, onRetry = onDismissError)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ErrorCard(error: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Card(onClick = onRetry ?: {}, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Tap to retry",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
