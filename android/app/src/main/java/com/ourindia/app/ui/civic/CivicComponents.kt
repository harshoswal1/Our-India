package com.ourindia.app.ui.civic

import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddRoad
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUpOffAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ourindia.app.data.civic.CivicConfig
import com.ourindia.app.data.civic.Complaint
import com.ourindia.app.ui.theme.CivicColors
import com.ourindia.app.ui.theme.Display
import java.time.Duration
import java.time.Instant

// ── Brand ────────────────────────────────────────────────────────────────────

/** NagrikVoice keeps its own mark inside Our India: serif wordmark, green dot. */
@Composable
fun NagrikVoiceMark(modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(CivicColors.Civic, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                "NagrikVoice",
                fontFamily = Display,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.headlineSmall,
                color = CivicColors.Navy
            )
        }
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary, modifier = Modifier.padding(start = 18.dp))
        }
    }
}

// ── Categories ───────────────────────────────────────────────────────────────

/**
 * [code] keys the app's municipal department directory (MunicipalData); [serverCode] is
 * what the NagrikVoice server routes on. The server only knows its own codes: sent a
 * [code], it finds no category mapping and emails every city's report to Kolhapur's
 * fallback address. So requests and filters use [serverCode], and [of] reads both.
 */
enum class IssueCategory(val code: String, val serverCode: String, val label: String, val icon: ImageVector, val subtypes: List<String>) {
    DISASTER("DISASTER_MANAGEMENT", "DISASTER_AND_SAFETY", "Disaster Management", Icons.Outlined.WarningAmber, listOf("Flood", "Fallen tree", "Wall collapse risk", "Fire hazard", "Landslide")),
    ELECTRICITY("ELECTRICITY_MSEDCL", "ELECTRICITY_AND_LIGHTING", "Electricity (MSEDCL)", Icons.Outlined.Lightbulb, listOf("Street light not working", "Exposed wiring", "Leaning pole", "Power outage", "Transformer issue")),
    HEALTHCARE("HEALTHCARE_DISTRICT_HEALTH", "PUBLIC_HEALTH_AND_SANITATION", "Healthcare", Icons.Outlined.HealthAndSafety, listOf("Mosquito breeding", "Sanitation issue", "Hospital complaint", "Vaccination", "Epidemic concern")),
    TAX("PROPERTY_TAX", "PROPERTY_TAX", "Property Tax", Icons.AutoMirrored.Outlined.ReceiptLong, listOf("Wrong assessment", "Receipt not issued", "Billing dispute", "Online portal issue")),
    PWD("PUBLIC_WORKS_PWD", "ROADS_AND_INFRASTRUCTURE", "Public Works (PWD)", Icons.Outlined.AddRoad, listOf("Pothole", "Broken footpath", "Damaged divider", "Road repair", "Bridge issue")),
    WATER("WATER_DEPARTMENT", "WATER_AND_DRAINAGE", "Water Dept.", Icons.Outlined.WaterDrop, listOf("Pipeline leak", "No water supply", "Contaminated water", "Blocked drain", "Waterlogging"));

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code || it.serverCode == code } ?: DISASTER
    }
}

// ── Status ───────────────────────────────────────────────────────────────────

data class StatusLook(val label: String, val fg: Color, val bg: Color)

/** One honest label per complaint state; delivery is called "demo" while it is simulated. */
fun statusLook(c: Complaint): StatusLook = when {
    c.resolutionStatus == "RESOLVED_VERIFIED" -> StatusLook("Resolved", CivicColors.CivicDeep, CivicColors.CivicContainer)
    c.resolutionStatus == "RESOLUTION_CLAIMED" || c.resolutionStatus == "RESOLUTION_UNDER_REVIEW" ->
        StatusLook("Fix under review", CivicColors.Violet, CivicColors.VioletContainer)
    c.resolutionStatus == "REOPENED" -> StatusLook("Reopened", CivicColors.Danger, CivicColors.DangerContainer)
    c.resolutionStatus == "INVALIDATED" -> StatusLook("Invalidated", CivicColors.TextSecondary, CivicColors.SurfaceVariant)
    c.emailStatus == "FAILED" -> StatusLook("Delivery failed", CivicColors.Danger, CivicColors.DangerContainer)
    c.emailStatus != "DELIVERED" -> StatusLook("Awaiting delivery", CivicColors.Amber, CivicColors.AmberContainer)
    c.overdue -> StatusLook("Overdue", CivicColors.Danger, CivicColors.DangerContainer)
    else -> StatusLook("Open", CivicColors.Navy, CivicColors.SurfaceVariant)
}

@Composable
fun StatusPill(look: StatusLook, modifier: Modifier = Modifier) {
    Text(
        look.label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = look.fg,
        modifier = modifier
            .background(look.bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun DemoDeliveryNote(modifier: Modifier = Modifier) {
    if (!CivicConfig.SIMULATED_DELIVERY) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CivicColors.AmberContainer, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Outlined.Science, contentDescription = null, tint = CivicColors.Amber, modifier = Modifier.size(18.dp))
        Text(
            "Demo delivery: the NagrikVoice server simulates the email to the corporation. No email has reached the officials yet.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6B4A12)
        )
    }
}

// ── Images ───────────────────────────────────────────────────────────────────

/**
 * Report photos are stored as data: URLs (the server has no upload endpoint), which Coil
 * can't fetch by URL — decode them to bytes once and hand Coil the ByteArray.
 */
@Composable
fun IssueImage(url: String, category: IssueCategory, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val model: Any? = remember(url) {
        when {
            url.startsWith("data:") -> runCatching { Base64.decode(url.substringAfter(","), Base64.DEFAULT) }.getOrNull()
            url.isNotBlank() -> url
            else -> null
        }
    }
    Box(modifier.background(CivicColors.SurfaceVariant), contentAlignment = Alignment.Center) {
        Icon(category.icon, contentDescription = null, tint = CivicColors.TextMuted, modifier = Modifier.size(32.dp))
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

// ── Time ─────────────────────────────────────────────────────────────────────

fun relativeTime(iso: String, now: Instant = Instant.now()): String {
    val then = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val d = Duration.between(then, now)
    return when {
        d.isNegative || d.toMinutes() < 1 -> "just now"
        d.toMinutes() < 60 -> "${d.toMinutes()} min ago"
        d.toHours() < 24 -> "${d.toHours()} h ago"
        d.toDays() < 30 -> "${d.toDays()} d ago"
        else -> "${d.toDays() / 30} mo ago"
    }
}

// ── Issue card ───────────────────────────────────────────────────────────────

@Composable
fun IssueCard(
    complaint: Complaint,
    onOpen: () -> Unit,
    onSupport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val category = IssueCategory.of(complaint.category)
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        color = CivicColors.Surface,
        border = BorderStroke(1.dp, CivicColors.Outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Box {
                IssueImage(
                    url = complaint.photoUrl,
                    category = category,
                    contentDescription = complaint.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                )
                StatusPill(statusLook(complaint), Modifier.align(Alignment.TopStart).padding(12.dp))
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    listOfNotNull(category.label, complaint.locality.ifBlank { null }, relativeTime(complaint.createdAt).ifBlank { null })
                        .joinToString("  ·  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = CivicColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(complaint.title, style = MaterialTheme.typography.titleMedium, color = CivicColors.Navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        complaint.authorityName ?: complaint.administrativeArea,
                        style = MaterialTheme.typography.bodySmall,
                        color = CivicColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    SupportButton(complaint.supportCount, complaint.supported, onSupport)
                }
            }
        }
    }
}

@Composable
fun SupportButton(count: Int, supported: Boolean, onClick: () -> Unit, height: Dp = 36.dp) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (supported) CivicColors.Civic else CivicColors.CivicContainer,
        modifier = Modifier.height(height)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                if (supported) Icons.Outlined.ThumbUp else Icons.Outlined.ThumbUpOffAlt,
                contentDescription = if (supported) "Remove support" else "Support this issue",
                tint = if (supported) Color.White else CivicColors.CivicDeep,
                modifier = Modifier.size(16.dp)
            )
            Text(
                if (count == 0) "Support" else "$count",
                style = MaterialTheme.typography.labelLarge,
                color = if (supported) Color.White else CivicColors.CivicDeep
            )
        }
    }
}

/** Shown while the free-tier server wakes from sleep, so a 30-second spinner has a reason. */
@Composable
fun WakingServerHint(modifier: Modifier = Modifier) {
    Text(
        "Waking the NagrikVoice server — the first load after a while can take up to a minute.",
        style = MaterialTheme.typography.bodySmall,
        color = CivicColors.TextSecondary,
        modifier = modifier
    )
}
