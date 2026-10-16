package com.ourindia.app.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ourindia.app.ui.theme.CivicColors
import kotlinx.coroutines.launch

/** First-launch flags. SharedPreferences: two booleans don't need a database. */
class FirstRunPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("first_run", Context.MODE_PRIVATE)
    var tutorialSeen: Boolean
        get() = prefs.getBoolean("tutorial_seen", false)
        set(v) = prefs.edit().putBoolean("tutorial_seen", v).apply()
    /** Set after the first system prompt, so a later "Allow" knows Android may no longer show it. */
    var locationAsked: Boolean
        get() = prefs.getBoolean("location_asked", false)
        set(v) = prefs.edit().putBoolean("location_asked", v).apply()
    var lawyerOpened: Boolean
        get() = prefs.getBoolean("lawyer_opened", false)
        set(v) = prefs.edit().putBoolean("lawyer_opened", v).apply()
    var locationNeverAsk: Boolean
        get() = prefs.getBoolean("location_never_ask", false)
        set(v) = prefs.edit().putBoolean("location_never_ask", v).apply()
}

val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

fun hasLocationPermission(context: Context) =
    LOCATION_PERMISSIONS.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

private data class Step(val icon: ImageVector, val tint: Color, val bg: Color, val title: String, val body: String, val points: List<String> = emptyList())

private val STEPS = listOf(
    Step(
        Icons.Outlined.Flag, CivicColors.SaffronDeep, CivicColors.Saffron.copy(alpha = 0.14f),
        "Welcome to Our India",
        "Two things in one app: who runs your area, and what needs fixing in it."
    ),
    Step(
        Icons.Outlined.DynamicFeed, CivicColors.CivicDeep, CivicColors.CivicContainer,
        "Your feed",
        "Home shows civic problems people have reported, newest first.",
        listOf("Double-tap a photo to support it. More support, higher priority.", "Share a report, or open it on the map.", "Saw it get repaired? Tap \"Fixed?\". A fix counts only once it's verified.")
    ),
    Step(
        Icons.Outlined.PhotoCamera, CivicColors.CivicDeep, CivicColors.CivicContainer,
        "Report a problem",
        "Tap the green Report button.",
        listOf("Take the photo on the spot. Gallery photos aren't accepted as evidence.", "Your location picks the municipal corporation it goes to.", "Choose what's wrong, add a title and send. You'll need a free account.")
    ),
    Step(
        Icons.Outlined.Timeline, CivicColors.Navy, CivicColors.SurfaceVariant,
        "Follow it through",
        "Every report keeps a public record: delivered, open, overdue, fix under review, resolved. If someone already reported the same spot, you'll be asked to support theirs instead.",
        listOf("Email delivery to corporations is still simulated. Such reports are labelled \"demo delivery\".")
    ),
    Step(
        Icons.Outlined.Hub, CivicColors.Navy, CivicColors.SurfaceVariant,
        "Who leads where",
        "Leaders shows each party's organisation as an orbit: the head in the middle, their units around them.",
        listOf("Tap a circle to move into it. Back steps out.", "Pinch to zoom; switch to Chart for the classic tree.", "Every name links to its source. Party heads are re-checked on Wikipedia every month.")
    ),
    Step(
        Icons.Outlined.Balance, CivicColors.Violet, CivicColors.VioletContainer,
        "Ask a lawyer",
        "The violet button on every tab opens the AI Lawyer.",
        listOf("Ask about police, property, money or consumer problems in plain words.", "Draft a legal notice, police complaint or RTI application.", "Look up old IPC sections under the new BNS, your rights, and helplines.")
    ),
    Step(
        Icons.Outlined.MyLocation, CivicColors.CivicDeep, CivicColors.CivicContainer,
        "Use your location",
        "So the app can show how far each report is, send yours to the right corporation, and find the party heads for your area. It's read only while you use the app and never shown to others."
    )
)

/**
 * First-launch walkthrough as a floating card. [onFinish] fires on Done or Skip; the
 * caller then asks for location, so the permission prompt always follows the explanation.
 */
@Composable
fun TutorialDialog(onFinish: () -> Unit) {
    val pager = rememberPagerState { STEPS.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == STEPS.lastIndex

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, dismissOnBackPress = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = CivicColors.Surface,
            shadowElevation = 12.dp,
            modifier = Modifier.padding(20.dp).widthIn(max = 420.dp).fillMaxWidth()
        ) {
            Column(Modifier.padding(vertical = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${pager.currentPage + 1} of ${STEPS.size}", style = MaterialTheme.typography.labelMedium, color = CivicColors.TextMuted, modifier = Modifier.weight(1f))
                    if (!last) TextButton(onClick = onFinish) { Text("Skip", color = CivicColors.TextSecondary) }
                }
                HorizontalPager(state = pager, verticalAlignment = Alignment.Top, modifier = Modifier.heightIn(min = 340.dp)) { i ->
                    StepPage(STEPS[i])
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(STEPS.size) { i ->
                        val on = i == pager.currentPage
                        val w by animateDpAsState(if (on) 22.dp else 8.dp, label = "dot")
                        val c by animateColorAsState(if (on) CivicColors.Navy else CivicColors.Outline, label = "dotColor")
                        Box(Modifier.padding(horizontal = 3.dp).height(8.dp).width(w).background(c, CircleShape))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (pager.currentPage > 0) {
                        OutlinedButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text("Back", color = CivicColors.Navy)
                        }
                    }
                    Button(
                        onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        colors = ButtonDefaults.buttonColors(containerColor = if (last) CivicColors.Civic else CivicColors.Navy),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text(if (last) "Allow location" else "Next") }
                }
            }
        }
    }
}

@Composable
private fun StepPage(s: Step) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(64.dp).background(s.bg, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            Icon(s.icon, contentDescription = null, tint = s.tint, modifier = Modifier.size(32.dp))
        }
        Text(s.title, style = MaterialTheme.typography.headlineSmall, color = CivicColors.Navy)
        Text(s.body, style = MaterialTheme.typography.bodyLarge, color = CivicColors.OnSurface)
        s.points.forEachIndexed { i, p ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(22.dp).background(s.bg, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = s.tint, textAlign = TextAlign.Center)
                }
                Text(p, style = MaterialTheme.typography.bodyMedium, color = CivicColors.TextSecondary, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Shown on later launches while location is denied. Android stops showing its own prompt
 * after the second refusal, so "Allow" falls back to the app's settings page then.
 */
@Composable
fun LocationPromptDialog(onAllow: () -> Unit, onNotNow: () -> Unit, onNever: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNotNow,
        icon = { Icon(Icons.Outlined.LocationOff, contentDescription = null, tint = CivicColors.SaffronDeep) },
        title = { Text("Location is off") },
        text = {
            Text(
                "Without it, reports can't be sent to the right corporation, the feed can't show what's near you, and Leaders can't find your local party heads. It's only read while you use the app.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = { TextButton(onClick = onAllow) { Text("Allow", color = CivicColors.CivicDeep) } },
        dismissButton = {
            Row {
                TextButton(onClick = onNever) { Text("Don't ask again", color = CivicColors.TextSecondary) }
                TextButton(onClick = onNotNow) { Text("Not now") }
            }
        }
    )
}

/** True when Android will still show its own permission prompt. */
fun canAskLocationAgain(activity: Activity?, prefs: FirstRunPrefs): Boolean =
    !prefs.locationAsked || (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION))

fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }
}

@Composable
fun rememberFirstRunPrefs(): FirstRunPrefs {
    val context = LocalContext.current
    return remember { FirstRunPrefs(context.applicationContext) }
}
