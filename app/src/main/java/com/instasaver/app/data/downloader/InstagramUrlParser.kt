package com.instasaver.app.data.downloader

import java.net.URI
import java.net.URLDecoder

/**
 * Identifies and normalizes Instagram URLs.
 */
object InstagramUrlParser {

    private val INSTAGRAM_HOSTS = setOf(
        "instagram.com",
        "www.instagram.com",
        "m.instagram.com",
        "instagr.am",
        "www.instagr.am"
    )

    private val VALID_PATH_PREFIXES = listOf("/reel/", "/reels/", "/p/", "/tv/")

    sealed class Result {
        data class Valid(val normalizedUrl: String, val shortcode: String) : Result()
        data class NotInstagram(val url: String) : Result()
        data class Invalid(val url: String) : Result()
    }

    fun parse(input: String?): Result {
        if (input.isNullOrBlank()) return Result.Invalid("")

        // The shared text may contain extra text; extract the first URL-like token.
        val candidate = extractUrl(input) ?: return Result.Invalid(input)

        val uri = try {
            URI(if (candidate.startsWith("http")) candidate else "https://$candidate")
        } catch (e: Exception) {
            return Result.Invalid(input)
        }

        val host = uri.host?.lowercase() ?: return Result.Invalid(input)
        if (host !in INSTAGRAM_HOSTS) return Result.NotInstagram(candidate)

        val path = uri.path ?: return Result.Invalid(input)
        val prefix = VALID_PATH_PREFIXES.firstOrNull { path.startsWith(it) }
            ?: return Result.Invalid(candidate)

        val rest = path.removePrefix(prefix).trim('/')
        val shortcode = rest.substringBefore('/').trim()
        if (shortcode.isBlank()) return Result.Invalid(candidate)

        val normalized = "https://www.instagram.com$prefix$shortcode/"
        return Result.Valid(normalized, shortcode)
    }

    fun isInstagramUrl(input: String?): Boolean = parse(input) is Result.Valid

    private fun extractUrl(text: String): String? {
        val regex = Regex("(https?://[^\\s]+|www\\.[^\\s]+|instagram\\.com/[^\\s]+|instagr\\.am/[^\\s]+)")
        return regex.find(text)?.value?.trimEnd('.', ',', ')', ']')
    }
}
