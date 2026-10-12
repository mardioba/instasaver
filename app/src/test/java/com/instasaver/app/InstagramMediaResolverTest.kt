package com.instasaver.app

import com.instasaver.app.data.downloader.InstagramMediaResolver
import com.instasaver.app.data.downloader.ResolveResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramMediaResolverTest {

    private val resolver = InstagramMediaResolver()

    @Test
    fun photoWithMusic_isResolvedAsHighQualityImage() {
        val html = """
            <html><head>
            <meta property="og:title" content="usuario"/>
            <meta property="og:image" content="https://scontent-gru2-1.cdninstagram.com/v/t51.2885-15/123_n.jpg"/>
            <script>{"clips_metadata":{"music_info":{"music_asset_info":{"audio_id":"456"}}},
            "display_url":"https:\/\/scontent-gru2-1.cdninstagram.com\/v\/t51.2885-15\/123.jpg"}</script>
            </head></html>
        """.trimIndent()

        val r = resolver.parsePublicPage(html)
        assertTrue(r is ResolveResult.Success)
        r as ResolveResult.Success
        assertFalse("post de foto não deve ser tratado como vídeo", r.media.isVideo)
        assertEquals(
            "https://scontent-gru2-1.cdninstagram.com/v/t51.2885-15/123.jpg",
            r.media.videoUrl
        )
    }

    @Test
    fun plainPhoto_withoutDisplayUrl_fallsBackToOgImage() {
        val html = """
            <html><head>
            <meta property="og:title" content="usuario"/>
            <meta property="og:image" content="https://scontent-gru1-1.cdninstagram.com/v/t51.2885-15/999.jpg"/>
            </head></html>
        """.trimIndent()

        val r = resolver.parsePublicPage(html)
        assertTrue(r is ResolveResult.Success)
        r as ResolveResult.Success
        assertFalse(r.media.isVideo)
        assertEquals(
            "https://scontent-gru1-1.cdninstagram.com/v/t51.2885-15/999.jpg",
            r.media.videoUrl
        )
    }

    @Test
    fun videoPost_isStillResolvedAsVideo() {
        val html = """
            <html><head>
            <meta property="og:title" content="usuario"/>
            <meta property="og:image" content="https://scontent-gru1-1.cdninstagram.com/v/thumb.jpg"/>
            <meta property="og:video" content="https://scontent-gru1-1.cdninstagram.com/v/video.mp4"/>
            </head></html>
        """.trimIndent()

        val r = resolver.parsePublicPage(html)
        assertTrue(r is ResolveResult.Success)
        r as ResolveResult.Success
        assertTrue(r.media.isVideo)
        assertEquals("https://scontent-gru1-1.cdninstagram.com/v/video.mp4", r.media.videoUrl)
    }

    @Test
    fun loginWall_isNotMistakenForAPhoto() {
        val html = """
            <html><head>
            <meta property="og:image" content="https://static.cdninstagram.com/rsrc.php/v3/yi/img/logo.png"/>
            </head><body>
            <a href="/accounts/login/?next=/p/ABC/">Log in</a>
            </body></html>
        """.trimIndent()

        val r = resolver.parsePublicPage(html)
        assertFalse("página de login não deve virar mídia", r is ResolveResult.Success)
        assertTrue(r is ResolveResult.PrivateOrUnavailable)
    }

    @Test
    fun jsonEscapedQueryParams_areDecoded() {
        val html = """
            <html><head>
            <script>{"display_url":"https:\/\/scontent-gru1-1.cdninstagram.com\/v\/t.jpg?_nc_cat=1\u0026cb=abc"}</script>
            </head></html>
        """.trimIndent()

        val r = resolver.parsePublicPage(html)
        assertTrue(r is ResolveResult.Success)
        r as ResolveResult.Success
        assertEquals(
            "https://scontent-gru1-1.cdninstagram.com/v/t.jpg?_nc_cat=1&cb=abc",
            r.media.videoUrl
        )
    }

    @Test
    fun photoWithoutAnyMedia_isUnsupported() {
        val html = """<html><head><title>Instagram</title></head></html>"""
        val r = resolver.parsePublicPage(html)
        assertTrue(r is ResolveResult.Unsupported)
    }
}
