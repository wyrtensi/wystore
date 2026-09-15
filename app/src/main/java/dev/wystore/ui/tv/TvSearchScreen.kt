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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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

/**
 * Search on a TV filters the TV catalogue as the user types.
 *
 * Every letter costs several presses on a remote, so results have to appear after two or three of
 * them, without waiting for a request - and the source's own search does not find TV-only apps at
 * all. Searching the whole of RuStore is one more button, for the phone apps the TV list lacks.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    catalogApps: List<StoreApp>,
    rustoreResults: List<StoreApp>,
    rustoreSearching: Boolean,
    packages: TvPackageContext,
    fieldFocus: FocusRequester,
    onSearchRustore: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Set when the remote's search or voice key brought the user here: listen straight away. */
    startVoice: Boolean = false,
    onVoiceStarted: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val trimmed = query.trim()
    val tvMatches = remember(trimmed, catalogApps) {
        if (trimmed.isEmpty()) emptyList()
        else catalogApps.filter { app ->
            app.name.contains(trimmed, ignoreCase = true) || app.packageName.contains(trimmed, ignoreCase = true)
        }
    }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TvOverscanHorizontal,
            end = TvOverscanHorizontal,
            top = TvOverscanVertical,
            bottom = TvOverscanVertical + 48.dp
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(key = "field") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    label = { androidx.compose.material3.Text(stringResource(R.string.search_field_placeholder)) },
                    leadingIcon = { androidx.compose.material3.Icon(Icons.Outlined.Search, contentDescription = null) },
                    shape = androidx.compose.material3.MaterialTheme.shapes.extraLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    // The keyboard's own "search" moves on to the results instead of leaving the
                    // focus in a field the user has finished with.
                    keyboardActions = KeyboardActions(onSearch = { focusManager.moveFocus(FocusDirection.Down) }),
                    // Takes what the buttons leave: their labels run longer in Russian than here.
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(fieldFocus)
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
                if (voiceIntent != null) {
                    TvIconButton(
                        icon = painterResource(R.drawable.ic_mic),
                        label = stringResource(R.string.tv_search_voice),
                        onClick = { runCatching { voiceLauncher.launch(voiceIntent) } }
                    )
                }
                TvSecondaryButton(
                    text = stringResource(R.string.tv_search_everywhere),
                    onClick = { onSearchRustore(trimmed) },
                    enabled = trimmed.isNotEmpty(),
                    icon = rememberIconPainter(Icons.Outlined.Search)
                )
            }
            Text(
                stringResource(R.string.tv_search_hint),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (trimmed.isNotEmpty()) {
            item(key = "tv") {
                TvSectionTitle(stringResource(R.string.tv_search_in_tv))
                if (tvMatches.isEmpty()) {
                    Text(stringResource(R.string.search_empty_title), style = MaterialTheme.typography.bodyLarge)
                } else {
                    TvResultRow(tvMatches, packages, onOpenApp)
                }
            }
        }
        if (rustoreSearching || rustoreResults.isNotEmpty()) {
            item(key = "rustore") {
                TvSectionTitle(stringResource(R.string.tv_search_in_rustore))
                if (rustoreSearching && rustoreResults.isEmpty()) {
                    Text(stringResource(R.string.vm_searching), style = MaterialTheme.typography.bodyLarge)
                } else {
                    TvResultRow(rustoreResults, packages, onOpenApp)
                }
            }
        }
    }
}

@Composable
private fun TvResultRow(apps: List<StoreApp>, packages: TvPackageContext, onOpenApp: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(TvRowHeight),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        items(apps, key = { it.packageName }) { app ->
            TvAppCard(app = app, state = rememberPackageState(app, packages), onClick = { onOpenApp(app.packageName) })
        }
    }
}
