package com.felipeftn.magnusorgue.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.felipeftn.magnusorgue.R
import com.felipeftn.magnusorgue.controller.OrganController
import kotlinx.coroutines.launch

/**
 * The one and only screen, stops-first: a slim monitor keyboard up top
 * (five octaves, mirrors the MIDI input) and the drawknob console filling
 * the rest. The player's hands belong on the real keyboard — the screen is
 * for registration.
 *
 * safeDrawingPadding keeps everything out from under notches and nav bars.
 */
@Composable
fun OrganScreen(controller: OrganController, onRetryAudio: () -> Unit) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Swipe-to-open would fight the keyboard's leftmost keys, so the
        // drawer only opens from the ☰ on the console.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = { SideDrawer(controller) },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
                .padding(8.dp)
        ) {
            if (!controller.audioReady) {
                AudioErrorBanner(onRetryAudio)
            }

            KeyboardView(
                controller = controller,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            )

            Spacer(Modifier.height(8.dp))

            StopsPanel(
                controller = controller,
                onOpenDrawer = { scope.launch { drawerState.open() } },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
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
