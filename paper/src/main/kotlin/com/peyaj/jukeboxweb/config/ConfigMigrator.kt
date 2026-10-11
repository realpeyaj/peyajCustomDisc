package com.peyaj.jukeboxweb.config

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.logging.Logger

object ConfigMigrator {

    data class SubKeyEntry(
        val key: String,
        val fullBlock: String
    )

    data class TopLevelEntry(
        val key: String,
        val fullBlock: String,
        val isSection: Boolean,
        val subKeys: List<SubKeyEntry> = emptyList()
    )

    /**
     * Parses the bundled template YAML text into structured top-level and subkey blocks.
     */
    fun parseTemplate(templateText: String): List<TopLevelEntry> {
        val lines = templateText.lines()
        val entries = mutableListOf<TopLevelEntry>()

        var currentTopKey: String? = null
        var isSection = false
        var topComments = mutableListOf<String>()
        val topBodyLines = mutableListOf<String>()
        val subKeys = mutableListOf<SubKeyEntry>()

        var currentSubKey: String? = null
        var subComments = mutableListOf<String>()
        val subBodyLines = mutableListOf<String>()

        val incomingTopComments = mutableListOf<String>()
        val incomingSubComments = mutableListOf<String>()

        fun flushSubKey() {
            if (currentSubKey != null) {
                val block = buildString {
                    for (c in subComments) appendLine(c)
                    for (b in subBodyLines) appendLine(b)
                }.trimEnd()
                subKeys.add(SubKeyEntry(currentSubKey!!, block))
                currentSubKey = null
                subComments = mutableListOf()
                subBodyLines.clear()
            }
        }

        fun flushTopLevel() {
            flushSubKey()
            if (currentTopKey != null) {
                val block = buildString {
                    for (c in topComments) appendLine(c)
                    for (b in topBodyLines) appendLine(b)
                }.trimEnd()
                entries.add(TopLevelEntry(currentTopKey!!, block, isSection, subKeys.toList()))
                currentTopKey = null
                isSection = false
                topComments = mutableListOf()
                topBodyLines.clear()
                subKeys.clear()
                incomingSubComments.clear()
            }
        }

        for (line in lines) {
            val trimmed = line.trim()
            val isCommentOrBlank = trimmed.startsWith("#") || trimmed.isEmpty()

            // Check if this line is an unindented top-level key (indent = 0)
            val isTopLevelKey = !line.startsWith(" ") && !line.startsWith("\t") &&
                    !isCommentOrBlank && line.contains(":") && !line.startsWith("-")

            if (isTopLevelKey) {
                flushTopLevel()
                val key = line.substringBefore(":").trim()
                val valPart = line.substringAfter(":").trim()
                currentTopKey = key
                isSection = valPart.isEmpty() || valPart.startsWith("#")
                topComments = incomingTopComments.toMutableList()
                incomingTopComments.clear()
                topBodyLines.add(line)
                continue
            }

            if (currentTopKey == null) {
                incomingTopComments.add(line)
                continue
            }

            // Inside a top-level section
            if (isSection) {
                topBodyLines.add(line)

                val isSubKey = (line.startsWith("  ") || line.startsWith("\t")) &&
                        !isCommentOrBlank && line.contains(":") && !line.trim().startsWith("-")

                if (isSubKey) {
                    flushSubKey()
                    val subKey = line.trim().substringBefore(":").trim()
                    currentSubKey = subKey
                    subComments = incomingSubComments.toMutableList()
                    incomingSubComments.clear()
                    subBodyLines.add(line)
                } else if (currentSubKey != null) {
                    if (isCommentOrBlank) {
                        incomingSubComments.add(line)
                    } else {
                        // Continuation line (e.g. list item under subkey)
                        subBodyLines.add(line)
                    }
                } else {
                    if (isCommentOrBlank) {
                        incomingSubComments.add(line)
                    }
                }
            } else {
                if (isCommentOrBlank) {
                    incomingTopComments.add(line)
                } else {
                    topBodyLines.add(line)
                }
            }
        }

        flushTopLevel()
        return entries
    }

    /**
     * Migrates the user's config content by injecting any missing top-level or subkey configuration settings.
     * Preserves all user formatting, custom comments, and existing option values.
     */
    fun migrate(userText: String, templateText: String): Pair<String, List<String>> {
        val templateEntries = parseTemplate(templateText)
        val addedKeys = mutableListOf<String>()
        var resultText = userText

        // 1. Check and inject missing sub-keys in existing top-level sections
        for (entry in templateEntries) {
            if (!entry.isSection || entry.subKeys.isEmpty()) continue

            // Check if top-level section exists in user config
            val sectionRegex = Regex("""(?m)^[ \t]*${Regex.escape(entry.key)}[ \t]*:""")
            if (!sectionRegex.containsMatchIn(resultText)) continue

            for (subKey in entry.subKeys) {
                val fullPath = "${entry.key}.${subKey.key}"
                val lines = resultText.lines()
                val sectionStartIdx = lines.indexOfFirst { line ->
                    line.matches(Regex("""^[ \t]*${Regex.escape(entry.key)}[ \t]*:.*"""))
                }
                if (sectionStartIdx == -1) continue

                // Locate the end of the section
                var lastIndentedIdx = sectionStartIdx
                var sectionEndIdx = lines.size
                for (i in (sectionStartIdx + 1) until lines.size) {
                    val l = lines[i]
                    val isIndented = l.startsWith(" ") || l.startsWith("\t")
                    val isBlank = l.isBlank()
                    if (isIndented) {
                        if (!isBlank) lastIndentedIdx = i
                    } else if (!isBlank) {
                        sectionEndIdx = i
                        break
                    }
                }

                // Check if subKey exists within this section
                val subKeyRegex = Regex("""^[ \t]+${Regex.escape(subKey.key)}[ \t]*:.*""")
                val exists = (sectionStartIdx until sectionEndIdx).any { lines[it].matches(subKeyRegex) }

                if (!exists) {
                    // Insert subKey right after lastIndentedIdx
                    val newLines = lines.toMutableList()
                    val insertionPoint = lastIndentedIdx + 1
                    val blockLines = subKey.fullBlock.lines()
                    newLines.addAll(insertionPoint, blockLines)
                    resultText = newLines.joinToString("\n")
                    addedKeys.add(fullPath)
                }
            }
        }

        // 2. Check and append missing top-level entries
        for (entry in templateEntries) {
            val topKeyRegex = Regex("""(?m)^[ \t]*${Regex.escape(entry.key)}[ \t]*:""")
            if (!topKeyRegex.containsMatchIn(resultText)) {
                val separator = when {
                    resultText.endsWith("\n\n") -> ""
                    resultText.endsWith("\n") -> "\n"
                    resultText.isEmpty() -> ""
                    else -> "\n\n"
                }
                val cleanedBlock = entry.fullBlock.trimStart('\r', '\n')
                resultText = resultText + separator + cleanedBlock + "\n"
                addedKeys.add(entry.key)
            }
        }

        return Pair(resultText, addedKeys)
    }

    /**
     * Updates the config file on disk if missing settings are detected in the bundled template.
     * Returns true if modifications were written to disk.
     */
    fun updateConfigFile(configFile: File, templateStream: InputStream, logger: Logger? = null): Boolean {
        if (!configFile.exists()) return false

        val userText = configFile.readText(StandardCharsets.UTF_8)
        val templateText = templateStream.reader(StandardCharsets.UTF_8).use { it.readText() }

        val (updatedText, addedKeys) = migrate(userText, templateText)

        if (addedKeys.isNotEmpty()) {
            configFile.writeText(updatedText, StandardCharsets.UTF_8)
            logger?.info("Automatically updated config.yml with missing settings: ${addedKeys.joinToString(", ")}")
            return true
        }

        return false
    }
}
