package com.sanaullah.smartprayermode.ui.prayermode

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sanaullah.smartprayermode.admin.DeviceOwnerHelper
import com.sanaullah.smartprayermode.scheduler.PrayerSession
import com.sanaullah.smartprayermode.scheduler.PrayerSessionManager
import com.sanaullah.smartprayermode.ui.theme.MosqueSilhouette
import com.sanaullah.smartprayermode.ui.rememberNowMillis
import com.sanaullah.smartprayermode.ui.theme.Gold
import com.sanaullah.smartprayermode.ui.theme.GoldSoft
import com.sanaullah.smartprayermode.ui.theme.Ivory
import com.sanaullah.smartprayermode.ui.theme.IslamicBackground
import com.sanaullah.smartprayermode.ui.theme.SmartPrayerTheme
import com.sanaullah.smartprayermode.util.Fmt
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Full-screen Prayer Mode. When this app is the Device Owner and allowlisted, it enters Lock
 * Task mode (kiosk); otherwise it is an ordinary full-screen activity and nothing is locked.
 * Emergency calling and a hold-to-exit button are always available.
 */
class PrayerModeActivity : ComponentActivity() {

    private var lockStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        // A stale/expired session (e.g. after a crash) must never leave this screen behind.
        val session = PrayerSession.load(this)
        if (session == null) {
            finish()
            return
        }

        hideSystemBars()
        enterLockTaskIfPermitted()

        // Back is intentionally inert; the hold-to-exit button is the always-available way out.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        setContent {
            SmartPrayerTheme {
                PrayerModeScreen(
                    prayerName = session.prayerName,
                    endMillis = session.endMillis,
                    startMillis = session.startMillis,
                    lockActive = lockStarted,
                    onEmergency = ::openEmergencyDialer,
                    onFinish = { PrayerSessionManager.end(applicationContext) },
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                PrayerSession.active.collect { active ->
                    if (active == null) leave()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun enterLockTaskIfPermitted() {
        try {
            if (DeviceOwnerHelper.canLock(this)) {
                startLockTask()
                lockStarted = true
            }
        } catch (e: Exception) {
            android.util.Log.w("PrayerMode", "Lock task not started: ${e.message}")
        }
    }

    private fun leave() {
        if (lockStarted) {
            runCatching { stopLockTask() }
            lockStarted = false
        }
        finish()
    }

    /** Opens the dialer (allowlisted for Lock Task) so emergency numbers can always be called. */
    private fun openEmergencyDialer() {
        try {
            startActivity(Intent(Intent.ACTION_DIAL))
        } catch (e: Exception) {
            android.util.Log.w("PrayerMode", "No dialer available: ${e.message}")
        }
    }

    override fun onDestroy() {
        if (lockStarted && isFinishing) runCatching { stopLockTask() }
        super.onDestroy()
    }
}

@Composable
private fun PrayerModeScreen(
    prayerName: String,
    startMillis: Long,
    endMillis: Long,
    lockActive: Boolean,
    onEmergency: () -> Unit,
    onFinish: () -> Unit,
) {
    val ctx = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(ctx) }
    val now by rememberNowMillis(1000L)
    val remaining = endMillis - now

    LaunchedEffect(remaining <= 0) {
        if (remaining <= 0) onFinish() // the end alarm is the backup; this is idempotent
    }

    val displayName = com.sanaullah.smartprayermode.domain.Prayer.fromName(prayerName)?.label ?: prayerName
    val total = (endMillis - startMillis).coerceAtLeast(1)

    Box(Modifier.fillMaxSize().background(IslamicBackground)) {
        MosqueSilhouette(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(130.dp))
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Lock, null, tint = Gold, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "Prayer Mode Active",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Gold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Please wait for Salah",
                    color = GoldSoft,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(displayName, style = MaterialTheme.typography.headlineLarge, color = Ivory)
                Text(
                    Fmt.clock(Instant.ofEpochMilli(now), ZoneId.systemDefault(), is24, seconds = true),
                    color = Ivory.copy(alpha = 0.8f),
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    Fmt.ms(remaining),
                    color = Gold,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.padding(top = 16.dp)
                )
                LinearProgressIndicator(
                    progress = { (1f - remaining.toFloat() / total).coerceIn(0f, 1f) },
                    color = Gold,
                    trackColor = Gold.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth(0.7f).padding(top = 8.dp)
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "اللَّهُمَّ تَقَبَّلْ مِنَّا",
                    color = GoldSoft,
                    fontSize = 28.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    "May Allah accept our prayer",
                    color = Ivory.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onEmergency,
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E), contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth(0.7f).height(52.dp)
                ) {
                    Icon(Icons.Filled.Phone, null, modifier = Modifier.padding(end = 8.dp))
                    Text("Emergency Call", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                HoldToExit(onExit = onFinish)
                Text(
                    if (lockActive) "Screen unlocks automatically after Salah"
                    else "Prayer Mode ends automatically after Salah",
                    color = Ivory.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 96.dp)
                )
            }
        }
    }
}

/** Safe exit: press and hold for 3 seconds to end Prayer Mode early. */
@Composable
private fun HoldToExit(onExit: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var holding by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxWidth(0.7f).height(40.dp)
            .background(Gold.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    holding = true
                    val job = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(3000, easing = LinearEasing))
                        onExit()
                    }
                    tryAwaitRelease()
                    job.cancel()
                    holding = false
                    scope.launch { progress.snapTo(0f) }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.fillMaxSize().alpha(0.35f)
        ) {
            Box(
                Modifier.fillMaxWidth(progress.value).fillMaxSize()
                    .background(Gold, RoundedCornerShape(20.dp))
            )
        }
        Text(
            if (holding) "Keep holding…" else "Hold to end Prayer Mode",
            color = GoldSoft,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
