package com.felipeftn.magnusorgue.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felipeftn.magnusorgue.BuildConfig
import com.felipeftn.magnusorgue.ui.theme.Gold

/**
 * The side drawer: everything that isn't playing. Links open in the
 * browser — this app has no WebView and no network code of its own,
 * which is a feature.
 */

private const val REPO = "https://github.com/FelipeFTN/MagnusOrgue"

@Composable
fun SideDrawer(midiDeviceName: String?) {
    val context = LocalContext.current
    fun open(url: String) =
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))

    ModalDrawerSheet(
        drawerContainerColor = Color(0xFF1A130C),
        modifier = Modifier.fillMaxHeight(),
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "MagnusOrgue",
                color = Gold,
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                letterSpacing = 2.sp,
            )
            Text(
                "v${BuildConfig.VERSION_NAME}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )

            SectionTitle("How to play")
            Body(
                "Plug a MIDI keyboard into the phone with a USB OTG cable — " +
                "it connects by itself (status: " +
                (midiDeviceName ?: "no device right now") + "). " +
                "Pull stops on the console; no stops pulled means silence, " +
                "just like the real thing."
            )
            Body(
                "Pistons 1–4 under the stops are combination pistons: " +
                "long-press to store the current registration, tap to recall it."
            )

            SectionTitle("Get more organs")
            Body(
                "The import feature can load GrandOrgue sample sets. " +
                "Good places to find them:"
            )
            LinkRow("Piotr Grabowski — free sets") { open("https://piotrgrabowski.pl/instruments/") }
            LinkRow("GrandOrgue project") { open("https://github.com/GrandOrgue/grandorgue") }

            SectionTitle("Project")
            LinkRow("Report a bug / request a feature") { open("$REPO/issues") }
            LinkRow("Source code (GPLv3)") { open(REPO) }

            SectionTitle("Credits")
            Body(
                "Pipe samples from the Giubiasco set (Mascioni organ, 2008) " +
                "recorded by Piotr Grabowski."
            )
            LinkRow("piotrgrabowski.pl") { open("https://piotrgrabowski.pl") }
            Body(
                "Built with Google Oboe for low-latency audio. " +
                "Free software under the GNU GPL v3."
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(
        text.uppercase(),
        color = Gold.copy(alpha = 0.55f),
        fontFamily = FontFamily.Serif,
        fontSize = 10.sp,
        letterSpacing = 3.sp,
    )
    HorizontalDivider(color = Gold.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        fontSize = 12.sp,
        lineHeight = 17.sp,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("→ $label", color = Gold, fontSize = 13.sp)
        }
    }
}
