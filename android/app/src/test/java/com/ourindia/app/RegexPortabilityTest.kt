package com.ourindia.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests run on the desktop JVM, whose regex engine accepts a bare "}" or "]". Android's
 * ICU engine does not: `Regex("""\{\{[^{}]*}}""")` passed every test here and crashed every
 * phone on first use (WikiLeaders, 2026-10-17). So check the source instead of the behaviour.
 */
class RegexPortabilityTest {
    private val literal = Regex("Regex\\(\\s*(\"\"\".*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\")", RegexOption.DOT_MATCHES_ALL)

    @Test
    fun everyRegexEscapesItsBracesAndClosingBrackets() {
        val src = File("src/main/java")
        val offenders = src.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            literal.findAll(f.readText()).mapNotNull { m ->
                val raw = m.groupValues[1].let { if (it.startsWith("\"\"\"")) it.removeSurrounding("\"\"\"") else it.removeSurrounding("\"").replace("\\\\", "\\") }
                val bare = raw
                    .replace(Regex("\\\\."), "")                  // escaped characters
                    .replace(Regex("\\{\\d+(,\\d*)?}"), "")       // quantifiers like {3,}
                    .replace(Regex("\\[[^\\]]*]"), "")             // character classes
                if ('{' in bare || '}' in bare || ']' in bare) "${f.name}: ${raw.take(60)}" else null
            }
        }.toList()
        assertTrue("Unescaped brace/bracket (crashes on Android):\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
