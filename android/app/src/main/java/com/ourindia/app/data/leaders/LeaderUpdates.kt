package com.ourindia.app.data.leaders

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.ourindia.app.data.local.entity.PartyNodeEntity
import kotlinx.coroutines.flow.Flow

/**
 * What Wikipedia's infobox says about one seeded post, checked monthly.
 *
 * Wikipedia and our sourced seed disagree for reasons other than news: spelling ("Damu G.
 * Naik"), a page nobody updated (INC Sikkim's was last edited in May 2026), or "TBA". So a
 * difference from the seed proves nothing. What matters is a *change*: [baseline] is the
 * infobox value the first time we looked, and only when Wikipedia moves away from it do we
 * show [current] instead of the seed.
 */
@Entity(tableName = "leader_updates")
data class LeaderUpdateEntity(
    @PrimaryKey val nodeId: String,
    /** Title after redirects, e.g. "Bharatiya Janata Party – Kerala". */
    val pageTitle: String,
    val baseline: String,
    val current: String,
    /** ISO date the infobox first differed from [baseline]; null while it hasn't. */
    val changedAt: String?,
    val checkedAt: String
)

@Dao
interface LeaderUpdateDao {
    @Query("SELECT * FROM leader_updates")
    fun observeAll(): Flow<List<LeaderUpdateEntity>>

    @Query("SELECT * FROM leader_updates")
    suspend fun all(): List<LeaderUpdateEntity>

    @Query("SELECT MAX(checkedAt) FROM leader_updates")
    fun observeLastChecked(): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<LeaderUpdateEntity>)
}

/** One infobox to read for one post. */
data class WikiTarget(
    val nodeId: String,
    val title: String,
    val fields: List<String>,
    /**
     * The resolved page title must contain this. "Bharatiya Janata Party, Chandigarh"
     * redirects to the national BJP page, whose president is not Chandigarh's.
     */
    val mustContain: String?
)

/** Pure rules for the Wikipedia check; [LeaderUpdateRepository] does the I/O. */
object WikiLeaders {
    const val VACANT = "Vacant"

    private val PRESIDENT = listOf("president")

    // Party page and the infobox field naming its head, checked 2026-10-16. TMC and the
    // Shinde Shiv Sena are absent: their infoboxes carry no head field (TMC's only
    // candidate is "founder", which never changes).
    private val NATIONAL = mapOf(
        "BJP" to ("Bharatiya Janata Party" to PRESIDENT),
        "INC" to ("Indian National Congress" to PRESIDENT),
        "AAP" to ("Aam Aadmi Party" to listOf("convener", "leader")),
        "NCP(SP)" to ("Nationalist Congress Party (Sharadchandra Pawar)" to PRESIDENT),
        "DMK" to ("Dravida Munnetra Kazhagam" to PRESIDENT),
        "BSP" to ("Bahujan Samaj Party" to PRESIDENT),
        "CPI(M)" to ("Communist Party of India (Marxist)" to listOf("general_secretary")),
        "NPP" to ("National People's Party (India)" to PRESIDENT),
        "SS(UBT)" to ("Shiv Sena (Uddhav Balasaheb Thackeray)" to PRESIDENT),
        "MNS" to ("Maharashtra Navnirman Sena" to PRESIDENT),
        "AIADMK" to ("All India Anna Dravida Munnetra Kazhagam" to listOf("general_secretary")),
        "TDP" to ("Telugu Desam Party" to PRESIDENT),
        "YSRCP" to ("YSR Congress Party" to PRESIDENT),
        "JSP" to ("Janasena Party" to PRESIDENT),
        "BRS" to ("Bharat Rashtra Samithi" to PRESIDENT),
        "JD(U)" to ("Janata Dal (United)" to PRESIDENT),
        "RJD" to ("Rashtriya Janata Dal" to PRESIDENT),
        "SP" to ("Samajwadi Party" to PRESIDENT),
        "BJD" to ("Biju Janata Dal" to listOf("president", "chairperson")),
        "JMM" to ("Jharkhand Mukti Morcha" to PRESIDENT),
        "SAD" to ("Shiromani Akali Dal" to PRESIDENT),
        "JD(S)" to ("Janata Dal (Secular)" to PRESIDENT),
        "JKNC" to ("Jammu & Kashmir National Conference" to PRESIDENT)
    )

    /** Boundary-file state names → Wikipedia's spelling. */
    private fun wikiState(state: String) = when (state) {
        "Andaman & Nicobar" -> "Andaman and Nicobar Islands"
        "Jammu & Kashmir" -> "Jammu and Kashmir"
        else -> state
    }

    /** National heads of mapped parties, and BJP/INC state presidents. Districts have no pages. */
    fun targets(nodes: List<PartyNodeEntity>): List<WikiTarget> = nodes.mapNotNull { n ->
        val state = n.state?.let(::wikiState)
        when {
            n.level == "NATIONAL" && n.parentId == null ->
                NATIONAL[n.partyName]?.let { (title, fields) -> WikiTarget(n.id, title, fields, null) }
            n.level != "STATE" || state == null || !n.roleTitle.contains("President") -> null
            n.partyName == "BJP" -> WikiTarget(n.id, "Bharatiya Janata Party, $state", PRESIDENT, state)
            // "Madhya Pradesh Congress Committee", not "Madhya Pradesh Pradesh …"; redirects cover the rest.
            n.partyName == "INC" -> WikiTarget(
                n.id,
                if (state.endsWith(" Pradesh")) "$state Congress Committee" else "$state Pradesh Congress Committee",
                PRESIDENT,
                state
            )
            else -> null
        }
    }

    private val REF = Regex("<ref[^>]*/>|<ref[^>]*>.*?</ref>|<ref.*$|<!--.*?-->")
    private val LINK = Regex("""\[\[([^\]|]+)(?:\|([^\]]+))?]]""")
    private val TEMPLATE = Regex("""\{\{[^{}]*}}""")
    private val TAG = Regex("<[^>]+>")
    private val HONORIFIC = Regex("""^(Dr\.?|Shri|Smt\.?|Adv\.?|Prof\.?)\s+""", RegexOption.IGNORE_CASE)

    /**
     * The first of [fields] that holds a name: a linked name's display text, else the
     * plain text. "Vacant"/"TBA" read as [VACANT]; an empty field reads as absent (null),
     * because editors often leave infobox rows blank rather than unknown.
     */
    fun infoboxValue(wikitext: String, fields: List<String>): String? {
        for (f in fields) {
            val line = Regex("""^\s*\|\s*$f\s*=(.*)$""", RegexOption.MULTILINE).find(wikitext)?.groupValues?.get(1) ?: continue
            val v = line.replace(REF, "")
            val name = LINK.find(v)?.let { m -> m.groupValues[2].ifBlank { m.groupValues[1].substringBefore(" (") } }
                ?: v.replace(TEMPLATE, "").replace(TAG, "").trim().takeUnless { it.startsWith("|") }
                ?: continue
            val clean = name.replace(HONORIFIC, "").substringBefore(" (").trim()
            if (clean.isBlank()) continue
            return if (clean.lowercase() in setOf("vacant", "tba", "tbd", "to be announced", "none")) VACANT else clean
        }
        return null
    }

    private val TITLES = setOf("dr", "shri", "smt", "adv", "prof", "sri")

    /** "Harshwardhan Sapkal" = "Harshwardhan Vasantrao Sapkal"; initials and titles ignored. */
    fun sameName(a: String, b: String): Boolean {
        fun tokens(s: String) = s.lowercase().replace(Regex("[^a-z]+"), " ").split(' ')
            .filter { it.length > 1 && it !in TITLES }.toSet()
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return a.trim().equals(b.trim(), ignoreCase = true)
        return ta.containsAll(tb) || tb.containsAll(ta)
    }

    /** Folds one fresh reading into the stored row. */
    fun next(old: LeaderUpdateEntity?, nodeId: String, page: String, value: String, today: String): LeaderUpdateEntity = when {
        old == null -> LeaderUpdateEntity(nodeId, page, baseline = value, current = value, changedAt = null, checkedAt = today)
        sameName(value, old.current) -> old.copy(pageTitle = page, checkedAt = today)
        // Reverted to what we first saw (often vandalism undone): nothing changed after all.
        sameName(value, old.baseline) -> old.copy(pageTitle = page, current = value, changedAt = null, checkedAt = today)
        else -> old.copy(pageTitle = page, current = value, changedAt = today, checkedAt = today)
    }

    /** The holder to show instead of the seed's, or null to keep the seed. */
    fun override(seedHolder: String, u: LeaderUpdateEntity?): String? =
        u?.takeIf { it.changedAt != null && !sameName(it.current, seedHolder) }?.current
}
