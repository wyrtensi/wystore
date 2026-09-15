package dev.wystore.ui.tv

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import dev.wystore.settings.TvCatalog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.wystore.R
import dev.wystore.data.StoreApp
import dev.wystore.data.GitHubCatalogEntry

/**
 * Search on a TV shows results as the user types or speaks.
 *
 * Every letter costs several presses on a remote, so the TV catalogue, held whole, is filtered on
 * the spot. The source's own search - the only way into the phone catalogue, and blind to TV-only
 * apps - runs by itself once typing pauses, when the catalogue setting includes phone apps; there
 * is no separate button to find and press for it.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    catalogMode: TvCatalog,
    catalogApps: List<StoreApp>,
    /** The query the source results below belong to, so results for an older one are not shown. */
    rustoreQuery: String,
    rustoreResults: List<StoreApp>,
    rustoreSearching: Boolean,
    packages: TvPackageContext,
    fieldFocus: FocusRequester,
    /** False while the focus is up in the menu: arriving here must not pull it down. */
    takeFocus: Boolean,
    onSearchRustore: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    /** GitHub projects matching the query, found locally; empty when GitHub is switched off. */
    githubResults: List<GitHubCatalogEntry>,
    onOpenGitHub: (GitHubCatalogEntry) -> Unit,
    modifier: Modifier = Modifier,
    /** Set when the remote's search or voice key brought the user here: listen straight away. */
    startVoice: Boolean = false,
    onVoiceStarted: () -> Unit = {},
    /** The result last opened - a package name or a GitHub key - to return the focus to after Back. */
    restoreFocusTo: String? = null
) {
    val focusManager = LocalFocusManager.current
    val trimmed = query.trim()
    val showsTv = catalogMode != TvCatalog.PHONE
    val showsRustore = catalogMode != TvCatalog.TV
    val tvPackages = remember(catalogApps) { catalogApps.map { it.packageName }.toSet() }
    val tvMatches = remember(trimmed, catalogApps, showsTv) {
        if (trimmed.isEmpty() || !showsTv) emptyList()
        else catalogApps.filter { app ->
            app.name.contains(trimmed, ignoreCase = true) || app.packageName.contains(trimmed, ignoreCase = true)
        }
    }
    val rustoreMatches = remember(trimmed, rustoreQuery, rustoreResults, tvMatches, showsRustore) {
        if (!showsRustore || trimmed.isEmpty() || !rustoreQuery.equals(trimmed, ignoreCase = true)) emptyList()
        else rustoreResults.filter { result -> tvMatches.none { it.packageName == result.packageName } }
    }
    var editing by remember { mutableStateOf(false) }
    val editFocus = remember { FocusRequester() }
    val startEditing = {
        editing = true
        runCatching { editFocus.requestFocus() }
        Unit
    }
    val shouldTakeFocus by rememberUpdatedState(takeFocus || startVoice)
    val restoreFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!shouldTakeFocus) return@LaunchedEffect
        // Back from a result's page lands on that result, as it does on Home, when it is still
        // among the results; otherwise on the field.
        val restored = restoreFocusTo != null && runCatching { restoreFocus.requestFocus() }.getOrDefault(false)
        if (!restored) runCatching { fieldFocus.requestFocus() }
    }

    // The source is asked once typing pauses, not on every letter.
    LaunchedEffect(trimmed, showsRustore) {
        if (!showsRustore || trimmed.length < MIN_SOURCE_QUERY || rustoreQuery.equals(trimmed, ignoreCase = true)) {
            return@LaunchedEffect
        }
        delay(SOURCE_SEARCH_DELAY_MILLIS)
        onSearchRustore(trimmed)
    }

    // Voice is how a TV is searched; typing with a remote is the fallback. The button exists only
    // where something on the device can recognise speech - plenty of boxes ship without it.
    val context = LocalContext.current
    val voiceIntent = remember(context) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .takeIf { it.resolveActivity(context.packageManager) != null }
    }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { spoken ->
                onQueryChange(spoken)
                // The field is done with: the results are where the next press should land.
                focusManager.moveFocus(FocusDirection.Down)
            }
    }
    LaunchedEffect(startVoice, voiceIntent) {
        if (startVoice && voiceIntent != null) {
            onVoiceStarted()
            runCatching { voiceLauncher.launch(voiceIntent) }
        }
    }

    val listState = rememberLazyListState()
    // Back from inside the screen sends the focus up to the menu; the screen goes back to its top
    // with it, so the menu is not left above a list scrolled halfway down.
    LaunchedEffect(takeFocus) { if (!takeFocus) listState.animateScrollToItem(0) }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TvOverscanHorizontal,
            end = TvOverscanHorizontal,
            top = 8.dp,
            bottom = TvOverscanVertical + 48.dp
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(key = "field") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (voiceIntent != null) {
                    // Before the field, where TV launchers put it, and the same height, so the two
                    // read as one control.
                    TvIconButton(
                        icon = painterResource(R.drawable.ic_mic),
                        label = stringResource(R.string.tv_search_voice),
                        onClick = { runCatching { voiceLauncher.launch(voiceIntent) } }
                    )
                }
                // On a TV a text field that takes the focus also takes the remote: the keyboard
                // comes up the moment the focus passes over it and swallows the arrows. So the remote
                // stops on a stand-in with the field's look, and OK - or a tap - starts typing.
                Box(
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    // A placeholder, not a floating label: the label adds a strip on top of the
                    // field that pushed it out of line with the microphone beside it.
                    placeholder = { androidx.compose.material3.Text(stringResource(R.string.search_field_placeholder)) },
                    leadingIcon = { androidx.compose.material3.Icon(Icons.Outlined.Search, contentDescription = null) },
                    shape = androidx.compose.material3.MaterialTheme.shapes.extraLarge,
                    // Filled like the phone's search bar, with no outline until it has the focus.
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.border
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    // The keyboard's own "search" moves on to the results instead of leaving the
                    // focus in a field the user has finished with.
                    keyboardActions = KeyboardActions(onSearch = {
                        if (showsRustore && trimmed.isNotEmpty()) onSearchRustore(trimmed)
                        focusManager.moveFocus(FocusDirection.Down)
                    }),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(editFocus)
                        .focusProperties { canFocus = editing }
                        .onFocusChanged { if (!it.hasFocus) editing = false }
                        // On a remote the arrows are the only way out of the field: inside it they
                        // would move the cursor and trap the focus. Text comes from the on-screen
                        // keyboard or the voice button, neither of which needs the cursor keys.
                        .onPreviewKeyEvent { event ->
                            val direction = when (event.key) {
                                Key.DirectionRight -> FocusDirection.Right
                                Key.DirectionLeft -> FocusDirection.Left
                                Key.DirectionDown -> FocusDirection.Down
                                Key.DirectionUp -> FocusDirection.Up
                                else -> null
                            } ?: return@onPreviewKeyEvent false
                            if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(direction)
                            true
                        }
                )
                if (!editing) {
                    val fieldShape = androidx.compose.material3.MaterialTheme.shapes.extraLarge
                    Surface(
                        onClick = startEditing,
                        modifier = Modifier
                            .matchParentSize()
                            .focusRequester(fieldFocus)
                            .tvPointerClick(onClick = startEditing),
                        shape = ClickableSurfaceDefaults.shape(fieldShape),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        border = ClickableSurfaceDefaults.border(focusedBorder = tvFocusBorder(fieldShape)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            pressedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    ) {}
                }
                }
            }
            Text(
                stringResource(R.string.tv_search_hint),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (trimmed.isNotEmpty() && showsTv) {
            item(key = "tv") {
                TvSectionTitle(stringResource(R.string.tv_search_in_tv))
                if (tvMatches.isEmpty()) {
                    Text(stringResource(R.string.search_empty_title), style = MaterialTheme.typography.bodyLarge)
                } else {
                    TvResultRow(tvMatches, packages, onOpenApp, markPhone = { false }, restore = restoreFocusTo to restoreFocus)
                }
            }
        }
        if (trimmed.isNotEmpty() && showsRustore) {
            item(key = "rustore") {
                TvSectionTitle(stringResource(R.string.tv_search_in_rustore))
                when {
                    rustoreMatches.isNotEmpty() -> TvResultRow(
                        rustoreMatches,
                        packages,
                        onOpenApp,
                        // Among TV results a phone app is marked; with the phone catalogue alone
                        // there is nothing to tell apart.
                        markPhone = { app -> catalogMode == TvCatalog.BOTH && app.packageName !in tvPackages },
                        restore = restoreFocusTo to restoreFocus
                    )
                    rustoreSearching || trimmed.length >= MIN_SOURCE_QUERY && !rustoreQuery.equals(trimmed, ignoreCase = true) ->
                        Text(stringResource(R.string.vm_searching), style = MaterialTheme.typography.bodyLarge)
                    else -> Text(stringResource(R.string.search_empty_title), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (trimmed.isNotEmpty() && githubResults.isNotEmpty()) {
            item(key = "github") {
                TvSectionTitle(stringResource(R.string.search_sources_github))
                TvGitHubResultRow(githubResults, onOpenGitHub, restore = restoreFocusTo to restoreFocus)
            }
        }
    }
}

@Composable
private fun TvGitHubResultRow(
    entries: List<GitHubCatalogEntry>,
    onOpenGitHub: (GitHubCatalogEntry) -> Unit,
    restore: Pair<String?, FocusRequester>
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(TvRowHeight),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        items(entries, key = { it.slug }) { entry ->
            TvGitHubCard(
                entry = entry,
                onClick = { onOpenGitHub(entry) },
                modifier = if (entry.tvKey() == restore.first) Modifier.focusRequester(restore.second) else Modifier
            )
        }
    }
}

@Composable
private fun TvResultRow(
    apps: List<StoreApp>,
    packages: TvPackageContext,
    onOpenApp: (String) -> Unit,
    markPhone: (StoreApp) -> Boolean,
    restore: Pair<String?, FocusRequester>
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(TvRowHeight),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        items(apps, key = { it.packageName }) { app ->
            TvAppCard(
                app = app,
                state = rememberPackageState(app, packages),
                onClick = { onOpenApp(app.packageName) },
                modifier = if (app.packageName == restore.first) Modifier.focusRequester(restore.second) else Modifier,
                forPhone = markPhone(app)
            )
        }
    }
}

private const val MIN_SOURCE_QUERY = 2
private const val SOURCE_SEARCH_DELAY_MILLIS = 700L
