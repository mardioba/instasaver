package com.instasaver.app

import com.instasaver.app.data.downloader.InstagramMediaResolver
import com.instasaver.app.data.downloader.ResolveResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun renderedPage_videoUrlInsideEscapedJson_isResolvedAsVideo() {
        // The outerHTML dumped by a WebView escapes forward slashes in JSON
        // strings, so the .mp4 URL is not a clean link — it must be unwrapped
        // from the video_url key.
        val html = """
            <html><body><script>
            {"graphql":{"shortcode_media":{"video_url":"https:\/\/scontent-gru1-1.cdninstagram.com\/v\/t66.1234-4\/video.mp4?_nc_cat=1",
            "display_url":"https:\/\/scontent-gru1-1.cdninstagram.com\/v\/t51.2885-15\/thumb.jpg"}}}
            </script></body></html>
        """.trimIndent()

        val media = resolver.extractFromRenderedPage(html)
        assertTrue("deveria achar o video_url escapado", media != null)
        assertTrue(media!!.isVideo)
        assertEquals(
            "https://scontent-gru1-1.cdninstagram.com/v/t66.1234-4/video.mp4?_nc_cat=1",
            media.videoUrl
        )
    }

    @Test
    fun renderedPage_photoWithMusic_onlyExposesDisplayUrl() {
        // A photo-with-music post has no video_url at all, only display_url.
        val html = """
            <html><body><script>
            {"graphql":{"shortcode_media":{"is_video":false,
            "clips_metadata":{"music_info":{"music_asset_info":{"audio_id":"777"}}},
            "display_url":"https:\/\/scontent-gru2-1.cdninstagram.com\/v\/t51.2885-15\/photo.jpg?_nc_cat=2"}}}
            </script></body></html>
        """.trimIndent()

        val media = resolver.extractFromRenderedPage(html)
        assertTrue("deveria achar a imagem do post", media != null)
        assertFalse("foto com música não é vídeo", media!!.isVideo)
        assertEquals(
            "https://scontent-gru2-1.cdninstagram.com/v/t51.2885-15/photo.jpg?_nc_cat=2",
            media.videoUrl
        )
    }

    @Test
    fun renderedPage_withoutMedia_returnsNull() {
        val html = """<html><body><div>carregando...</div></body></html>"""
        assertNull(resolver.extractFromRenderedPage(html))
    }

    @Test
    fun mediaFromJsUrl_mp4_isVideo() {
        val url = "https://scontent-gru1-1.cdninstagram.com/v/t66.1234-4/video.mp4?_nc_cat=1"
        val media = resolver.mediaFromJsUrl(url, isVideo = true)
        assertTrue(media != null)
        assertTrue(media!!.isVideo)
        assertEquals(url, media.videoUrl)
    }

    @Test
    fun mediaFromJsUrl_photo_isNotVideo() {
        val url = "https://scontent-gru2-1.cdninstagram.com/v/t51.2885-15/photo.jpg?_nc_cat=2"
        val media = resolver.mediaFromJsUrl(url, isVideo = false)
        assertTrue(media != null)
        assertFalse("foto não é vídeo", media!!.isVideo)
        assertEquals(url, media.videoUrl)
    }

    @Test
    fun mediaFromJsUrl_fbcdnHost_isAccepted() {
        // Host atual do Instagram: instagram.*.fna.fbcdn.net (não contém "scontent").
        val url = "https://instagram.fssa7-1.fna.fbcdn.net/v/t51.82787-15/845941294_123_456.jpg?_nc_cat=1"
        val media = resolver.mediaFromJsUrl(url, isVideo = false)
        assertTrue("fbcdn é host de mídia válido", media != null)
        assertFalse(media!!.isVideo)
        assertEquals(url, media.videoUrl)
    }

    @Test
    fun isMediaHost_reconheceHostsDaMeta() {
        assertTrue(resolver.isMediaHost("https://scontent-gru1-1.cdninstagram.com/v/x.jpg"))
        assertTrue(resolver.isMediaHost("https://instagram.fssa7-1.fna.fbcdn.net/v/x.jpg"))
        // Host de JS/CSS e ícones — não é mídia.
        assertFalse(resolver.isMediaHost("https://static.cdninstagram.com/rsrc.php/v3/logo.png"))
        assertFalse(resolver.isMediaHost("https://example.com/x.jpg"))
    }

    @Test
    fun mediaFromJsUrl_nonCdnUrl_isRejected() {
        assertNull(resolver.mediaFromJsUrl("https://example.com/photo.jpg", isVideo = false))
    }

    @Test
    fun mediaFromJsUrl_httpNaoHttps_isRejected() {
        assertNull(resolver.mediaFromJsUrl("http://scontent.cdninstagram.com/photo.jpg", isVideo = false))
    }
}
