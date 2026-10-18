package dev.wystore.ui.library

import dev.wystore.ui.components.HubTextAction
import dev.wystore.ui.components.DialogButton
import dev.wystore.ui.components.HubAlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.wystore.AdoptionPrompt
import dev.wystore.R
import dev.wystore.data.AdoptionOption
import dev.wystore.data.InstallSource
import dev.wystore.ui.components.WySpinner

/**
 * Asks where updates for an app being accepted should come from.
 *
 * For an app from Google every option ends in an uninstall, so the dialog says that first. For
 * anything else the same package in RuStore or a repository the catalogue knows keeps the app as
 * it is, and those are offered as such; a replacement is marked as one. When nothing is found the
 * dialog says so and nothing is accepted - only a GitHub repository typed in can still be used.
 */
@Composable
fun AdoptionDialog(
    prompt: AdoptionPrompt,
    onPick: (AdoptionOption) -> Unit,
    onGitHub: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val fromGoogle = prompt.app.source == InstallSource.GOOGLE_PLAY
    HubAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (fromGoogle) R.string.adopt_google_title else R.string.dialog_adopt_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(
                        if (fromGoogle) R.string.adopt_google_text else R.string.adopt_source_question,
                        prompt.app.label
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                when {
                    prompt.loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WySpinner(size = 18.dp, strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.adopt_google_searching),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    prompt.options.isEmpty() && prompt.lookupFailed -> Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            stringResource(R.string.adopt_lookup_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        HubTextAction(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
                    }

                    prompt.options.isEmpty() -> Text(
                        stringResource(if (fromGoogle) R.string.adopt_google_not_found else R.string.adopt_not_found),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            optionsHeading(prompt, fromGoogle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        prompt.options.forEachIndexed { index, option ->
                            OptionRow(option = option, leading = index == 0, onPick = { onPick(option) })
                        }
                    }
                }
                if (!fromGoogle && !prompt.loading) {
                    GitHubLinkSection(prompt, onGitHub)
                }
                if (prompt.waiting > 0) {
                    Text(
                        stringResource(R.string.adopt_waiting, prompt.waiting),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            DialogButton(onClick = onDismiss) {
                Text(stringResource(if (prompt.waiting > 0) R.string.adopt_skip else R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun optionsHeading(prompt: AdoptionPrompt, fromGoogle: Boolean): String {
    val options = prompt.options
    val first = options.first()
    return when {
        fromGoogle -> stringResource(
            if ((first as? AdoptionOption.Replace)?.candidate?.exact == true) R.string.adopt_google_exact
            else R.string.adopt_google_pick
        )
        options.size > 1 && options.all { it.inPlace } -> stringResource(R.string.adopt_choose_source)
        first is AdoptionOption.Replace && first.candidate.exact -> stringResource(R.string.adopt_replace_signature)
        first is AdoptionOption.Replace -> stringResource(R.string.adopt_replace_by_name)
        else -> stringResource(R.string.adopt_found_in_place)
    }
}

/**
 * Updates from a repository the user names, for an app the store and the catalogue do not know -
 * an APK someone downloaded from GitHub by hand. It used to need a trip to Settings and a fresh
 * install from there.
 */
@Composable
private fun GitHubLinkSection(prompt: AdoptionPrompt, onGitHub: (String) -> Unit) {
    var open by rememberSaveable(prompt.app.packageName) { mutableStateOf(prompt.options.isEmpty()) }
    var link by rememberSaveable(prompt.app.packageName) { mutableStateOf("") }
    if (!open) {
        DialogButton(onClick = { open = true }) { Text(stringResource(R.string.adopt_github_link_open)) }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !prompt.githubChecking,
            label = { Text(stringResource(R.string.github_repo_url_label)) },
            placeholder = { Text("https://github.com/owner/repository") },
            isError = prompt.githubError != null,
            supportingText = prompt.githubError?.let { error -> { Text(error) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (link.isNotBlank()) onGitHub(link.trim()) })
        )
        Button(
            onClick = { onGitHub(link.trim()) },
            enabled = link.isNotBlank() && !prompt.githubChecking,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (prompt.githubChecking) {
                WySpinner(size = 18.dp, strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.adopt_github_confirm), maxLines = 1)
            }
        }
    }
}

@Composable
private fun OptionRow(
    option: AdoptionOption,
    leading: Boolean,
    onPick: () -> Unit
) {
    val iconSize = if (leading) 48.dp else 32.dp
    val (label, line, icon, action) = when (option) {
        is AdoptionOption.RuStore -> OptionText(
            option.candidate.label,
            stringResource(R.string.adopt_option_rustore),
            option.candidate.iconUrl,
            stringResource(R.string.dialog_adopt_confirm)
        )
        is AdoptionOption.GitHub -> OptionText(
            option.title,
            stringResource(R.string.adopt_option_github, option.repository.displayName),
            option.iconUrl,
            stringResource(R.string.dialog_adopt_confirm)
        )
        is AdoptionOption.Replace -> OptionText(
            option.candidate.label,
            option.candidate.packageName,
            option.candidate.iconUrl,
            stringResource(R.string.adopt_google_confirm)
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onPick),
        shape = MaterialTheme.shapes.medium,
        color = if (leading) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AsyncImage(
                model = icon,
                contentDescription = null,
                placeholder = if (option is AdoptionOption.GitHub) painterResource(R.drawable.ic_github) else null,
                error = if (option is AdoptionOption.GitHub) painterResource(R.drawable.ic_github) else null,
                modifier = Modifier.size(iconSize).clip(MaterialTheme.shapes.small)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    label,
                    style = if (leading) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    line,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
    }
}

private data class OptionText(val label: String, val line: String, val icon: String?, val action: String)
