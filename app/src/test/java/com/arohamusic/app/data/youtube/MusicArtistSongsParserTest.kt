package com.arohamusic.app.data.youtube

import org.junit.Assert.*
import org.junit.Test

class MusicArtistSongsParserTest {
    @Test fun selectsSongsFooterWithoutFollowingAlbumOrRelatedArtistLinks() {
        val raw = """{"contents":[
          {"musicShelfRenderer":{"title":{"runs":[{"text":"Albums"}]},"bottomEndpoint":{"browseEndpoint":{"browseId":"MPRE_album"}}}},
          {"musicShelfRenderer":{"title":{"runs":[{"text":"Songs"}]},"bottomEndpoint":{"browseEndpoint":{"browseId":"VL_artist_catalog","params":"catalog-params"}}}},
          {"musicShelfRenderer":{"title":{"runs":[{"text":"Related artists"}]},"bottomEndpoint":{"browseEndpoint":{"browseId":"UC_unrelated"}}}}
        ]}"""
        assertEquals(ArtistSongsEndpoint("VL_artist_catalog", "catalog-params"), MusicArtistSongsParser.endpoint(raw))
    }
    @Test fun readsBothMusicContinuationFormats() {
        assertEquals("next-page", MusicArtistSongsParser.continuation("""{"continuations":[{"nextContinuationData":{"continuation":"next-page"}}]}"""))
        assertEquals("next-item", MusicArtistSongsParser.continuation("""{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"next-item"}}}}"""))
        assertNull(MusicArtistSongsParser.continuation("""{"watchEndpoint":{"videoId":"abcdefghijk"}}"""))
    }
    @Test fun malformedOrAbsentCatalogEndpointDoesNotBecomeAGlobalSearch() {
        assertNull(MusicArtistSongsParser.endpoint("""{"musicShelfRenderer":{"title":{"simpleText":"Songs"},"bottomEndpoint":{"browseEndpoint":{"browseId":"https://unrelated.example"}}}}"""))
        assertNull(MusicArtistSongsParser.endpoint("{}"))
    }
}
