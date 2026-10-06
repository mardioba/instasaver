package com.instasaver.app

import com.instasaver.app.data.downloader.InstagramUrlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class InstagramUrlParserTest {

    @Test
    fun reelUrl_isValid_andNormalized() {
        val r = InstagramUrlParser.parse("https://www.instagram.com/reel/ABC123/?utm_source=share")
        assertTrue(r is InstagramUrlParser.Result.Valid)
        val v = r as InstagramUrlParser.Result.Valid
        assertEquals("ABC123", v.shortcode)
        assertEquals("https://www.instagram.com/reel/ABC123/", v.normalizedUrl)
    }

    @Test
    fun shortHost_isValid() {
        val r = InstagramUrlParser.parse("https://instagr.am/p/XYZ789/")
        assertTrue(r is InstagramUrlParser.Result.Valid)
        val v = r as InstagramUrlParser.Result.Valid
        assertEquals("https://www.instagram.com/p/XYZ789/", v.normalizedUrl)
    }

    @Test
    fun nonInstagram_isRejected() {
        val r = InstagramUrlParser.parse("https://example.com/reel/ABC123/")
        assertTrue(r is InstagramUrlParser.Result.NotInstagram)
    }

    @Test
    fun randomText_isInvalid() {
        val r = InstagramUrlParser.parse("veja esse vídeo legal")
        assertTrue(r is InstagramUrlParser.Result.Invalid)
    }

    @Test
    fun sharedTextWithUrl_extractsUrl() {
        val r = InstagramUrlParser.parse("Olha esse vídeo! https://www.instagram.com/tv/ABC123/ ótimo")
        assertTrue(r is InstagramUrlParser.Result.Valid)
    }
}
