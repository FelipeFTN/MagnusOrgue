package com.felipeftn.magnusorgue.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.felipeftn.magnusorgue.R
import com.felipeftn.magnusorgue.controller.OrganController

/**
 * The one and only screen: top bar with the controls, keyboard filling the
 * rest. safeDrawingPadding keeps the keys out from under notches and nav
 * bars — losing the top C to a camera cutout is not a good look.
 */
@Composable
fun OrganScreen(controller: OrganController, onRetryAudio: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        if (!controller.audioReady) {
            AudioErrorBanner(onRetryAudio)
        }

        TopBar(controller)

        KeyboardView(
            controller = controller,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        )
    }
}

/** The app's only real error state: the audio stream wouldn't open. */
@Composable
private fun AudioErrorBanner(onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.error) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.audio_error),
                color = MaterialTheme.colorScheme.onError,
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry), color = MaterialTheme.colorScheme.onError)
            }
        }
    }
}
