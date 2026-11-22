package com.sanaullah.smartprayermode.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanaullah.smartprayermode.audio.AzanLibrary
import com.sanaullah.smartprayermode.audio.AzanPlayer
import com.sanaullah.smartprayermode.audio.AzanSource
import com.sanaullah.smartprayermode.ui.AppViewModel
import com.sanaullah.smartprayermode.ui.GoldButton
import com.sanaullah.smartprayermode.ui.GoldCard
import com.sanaullah.smartprayermode.ui.NavRow
import com.sanaullah.smartprayermode.ui.ScreenScaffold
import com.sanaullah.smartprayermode.ui.SectionLabel
import com.sanaullah.smartprayermode.ui.SwitchRow
import com.sanaullah.smartprayermode.ui.theme.Emerald
import com.sanaullah.smartprayermode.ui.theme.Gold
import com.sanaullah.smartprayermode.ui.theme.Ivory
import com.sanaullah.smartprayermode.util.Fmt

@Composable
fun AzanSelectionScreen(vm: AppViewModel, onBack: () -> Unit, onOpenPlayer: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val azan by vm.azanState.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val s = settings ?: return
    val selected = AzanSource.fromName(s.azanSource)
    var volume by remember(s.azanVolume) { mutableFloatStateOf(s.azanVolume) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.update { it.copy(customAzanUri = uri.toString(), azanSource = AzanSource.CUSTOM.name) }
        }
    }

    ScreenScaffold("Azan voice", onBack) {
        GoldCard {
            SwitchRow(
                "Azan audio", "Play the Azan when a prayer time starts",
                s.azanEnabled, { v -> vm.update { it.copy(azanEnabled = v) } },
                icon = Icons.Filled.VolumeUp
            )
        }

        SectionLabel("Select Azan")
        GoldCard {
            AzanSource.entries.forEach { source ->
                val available = AzanLibrary.isAvailable(ctx, source, s.customAzanUri)
                val playingThis = azan.state == AzanPlayer.State.PLAYING && azan.label == source.label
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (source == AzanSource.CUSTOM && s.customAzanUri == null) {
                            picker.launch(arrayOf("audio/*"))
                        } else {
                            vm.update { it.copy(azanSource = source.name) }
                        }
                    }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected == source,
                        onClick = {
                            if (source == AzanSource.CUSTOM && s.customAzanUri == null) {
                                picker.launch(arrayOf("audio/*"))
                            } else {
                                vm.update { it.copy(azanSource = source.name) }
                            }
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = Gold, unselectedColor = Ivory.copy(alpha = 0.6f))
                    )
                    Column(Modifier.weight(1f)) {
                        Text(source.label, color = Ivory, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (available) {
                                if (source == AzanSource.CUSTOM) "File selected" else "Audio file included"
                            } else source.note,
                            color = if (available) Emerald else Gold.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(
                        onClick = { if (playingThis) vm.stopAzan() else vm.previewAzan(source) },
                        enabled = available
                    ) {
                        Icon(
                            if (playingThis) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            if (playingThis) "Stop preview" else "Preview ${source.label}",
                            tint = if (available) Gold else Ivory.copy(alpha = 0.3f)
                        )
                    }
                }
            }
            NavRow(
                if (s.customAzanUri == null) "Choose custom audio file" else "Change custom audio file",
                icon = Icons.Filled.MusicNote
            ) { picker.launch(arrayOf("audio/*")) }
        }

        SectionLabel("Volume")
        GoldCard {
            Text("Azan volume: ${(volume * 100).toInt()}%", color = Ivory)
            Slider(
                value = volume,
                onValueChange = { volume = it; vm.setLiveVolume(it) },
                onValueChangeFinished = { vm.update { it.copy(azanVolume = volume) } },
                colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold, inactiveTrackColor = Gold.copy(alpha = 0.25f))
            )
        }

        azan.message?.let { Text(it, color = Gold) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GoldButton(
                if (azan.state == AzanPlayer.State.IDLE) "Test Azan" else "Stop",
                onClick = { if (azan.state == AzanPlayer.State.IDLE) vm.playSelectedAzan() else vm.stopAzan() },
                modifier = Modifier.weight(1f),
                icon = if (azan.state == AzanPlayer.State.IDLE) Icons.Filled.PlayArrow else Icons.Filled.Stop
            )
            GoldButton("Audio player", onOpenPlayer, Modifier.weight(1f))
        }

        GoldCard {
            Text("Audio licensing", color = Gold, style = MaterialTheme.typography.labelLarge)
            Text(
                "No Azan recordings are bundled with this app. Add only recordings you are licensed to " +
                    "use (see assets/azan/README.md and docs/AUDIO_LICENSES.md), or pick your own file as " +
                    "the Custom Azan. Entries marked “must be supplied” stay disabled until a file is added.",
                color = Ivory.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun AudioPlayerScreen(vm: AppViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val azan by vm.azanState.collectAsStateWithLifecycle()
    val s = settings ?: return
    val source = AzanSource.fromName(s.azanSource)
    var volume by remember(s.azanVolume) { mutableFloatStateOf(s.azanVolume) }
    val idle = azan.state == AzanPlayer.State.IDLE

    ScreenScaffold("Azan player", onBack) {
        GoldCard(highlight = true) {
            Text("Selected Azan", color = Ivory.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            Text(source.label, color = Ivory, style = MaterialTheme.typography.headlineSmall)
            Text(
                when (azan.state) {
                    AzanPlayer.State.PLAYING -> "Playing"
                    AzanPlayer.State.PAUSED -> "Paused"
                    AzanPlayer.State.IDLE -> "Ready"
                },
                color = Gold, fontFamily = FontFamily.Serif, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
            )
        }

        GoldCard {
            val progress = if (azan.durationMs > 0) azan.positionMs.toFloat() / azan.durationMs else 0f
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = Gold, trackColor = Gold.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(Fmt.mmss(azan.positionMs), color = Ivory.copy(alpha = 0.8f))
                Text(Fmt.mmss(azan.durationMs), color = Ivory.copy(alpha = 0.8f))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (idle) vm.playSelectedAzan() else vm.togglePause() }) {
                    Icon(
                        if (azan.state == AzanPlayer.State.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (azan.state == AzanPlayer.State.PLAYING) "Pause" else "Play",
                        tint = Gold, modifier = Modifier.padding(4.dp)
                    )
                }
                IconButton(onClick = { vm.stopAzan() }, enabled = !idle) {
                    Icon(Icons.Filled.Stop, "Stop", tint = if (idle) Ivory.copy(alpha = 0.3f) else Gold)
                }
            }
        }

        GoldCard {
            Text("Volume: ${(volume * 100).toInt()}%", color = Ivory)
            Slider(
                value = volume,
                onValueChange = { volume = it; vm.setLiveVolume(it) },
                onValueChangeFinished = { vm.update { it.copy(azanVolume = volume) } },
                colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold, inactiveTrackColor = Gold.copy(alpha = 0.25f))
            )
        }
        azan.message?.let {
            Text(it, color = Gold)
        }
    }
}
