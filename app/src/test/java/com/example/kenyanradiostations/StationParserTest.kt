package com.example.kenyanradiostations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StationParserTest {

    // region Catalogue scraping

    @Test
    fun `parses id name and logo from a station entry`() {
        val stations = StationParser.parseStations(
            """
            <ul>
              <li class="item-1">
                <a href="/stations#citizen-fm" title="Citizen FM 91.5">
                  <img src="https://cdn.example/citizen.png" />
                </a>
              </li>
            </ul>
            """.trimIndent()
        )

        assertEquals(1, stations.size)
        assertEquals("citizen-fm", stations[0].id)
        assertEquals("Citizen FM 91.5", stations[0].name)
        assertEquals("https://cdn.example/citizen.png", stations[0].logoUrl)
    }

    @Test
    fun `skips entries without an id or a title`() {
        val stations = StationParser.parseStations(
            """
            <ul>
              <li class="item-1"><a href="/stations" title="No anchor"><img src="a.png"/></a></li>
              <li class="item-2"><a href="/s#has-id" title=""><img src="b.png"/></a></li>
              <li class="item-3"><a href="/s#good" title="Good"><img src="c.png"/></a></li>
            </ul>
            """.trimIndent()
        )

        assertEquals(listOf("good"), stations.map { it.id })
    }

    @Test
    fun `drops duplicate ids`() {
        val stations = StationParser.parseStations(
            """
            <ul>
              <li class="item-1"><a href="#kiss" title="Kiss 100"><img src="a.png"/></a></li>
              <li class="item-2"><a href="#kiss" title="Kiss 100 (again)"><img src="b.png"/></a></li>
            </ul>
            """.trimIndent()
        )

        assertEquals(1, stations.size)
        assertEquals("Kiss 100", stations[0].name)
    }

    @Test
    fun `ignores markup that is not a station item`() {
        val stations = StationParser.parseStations(
            """<ul><li class="nav-link"><a href="#x" title="Nav"><img src="a.png"/></a></li></ul>"""
        )
        assertEquals(emptyList<RadioStation>(), stations)
    }

    // endregion

    // region Stream selection

    private val station = RadioStation("kiss", "Kiss 100", "https://cdn.example/kiss.png")

    private fun response(vararg streams: Pair<String, String>): String {
        val body = streams.joinToString(",") { (type, url) ->
            """{"mediaType":"$type","url":"$url"}"""
        }
        return """
            {"success":true,"result":{
              "station":{"title":"Kiss 100.3","signal":"100.3 FM"},
              "streams":[$body]}}
        """.trimIndent()
    }

    @Test
    fun `playback prefers HLS over AAC and MP3`() {
        val details = StationParser.parseStationDetails(
            response("MP3" to "http://a/mp3", "AAC" to "http://a/aac", "HLS" to "http://a/hls"),
            station
        )!!

        assertEquals("http://a/hls", details.streamUrl)
    }

    @Test
    fun `playback falls back to AAC then MP3`() {
        assertEquals(
            "http://a/aac",
            StationParser.parseStationDetails(
                response("MP3" to "http://a/mp3", "AAC" to "http://a/aac"), station
            )!!.streamUrl
        )
        assertEquals(
            "http://a/mp3",
            StationParser.parseStationDetails(response("MP3" to "http://a/mp3"), station)!!.streamUrl
        )
    }

    @Test
    fun `recording prefers MP3 and reports the real container`() {
        val details = StationParser.parseStationDetails(
            response("HLS" to "http://a/hls", "AAC" to "http://a/aac", "MP3" to "http://a/mp3"),
            station
        )!!

        assertEquals("http://a/mp3", details.recordableUrl)
        assertEquals("mp3", details.recordingExtension)
        assertEquals("audio/mpeg", details.recordingMimeType)
    }

    @Test
    fun `recording falls back to AAC and does not claim to be MP3`() {
        val details = StationParser.parseStationDetails(
            response("HLS" to "http://a/hls", "AAC" to "http://a/aac"), station
        )!!

        assertEquals("http://a/aac", details.recordableUrl)
        assertEquals("aac", details.recordingExtension)
        assertEquals("audio/aac", details.recordingMimeType)
    }

    @Test
    fun `HLS-only stations are not recordable`() {
        val details = StationParser.parseStationDetails(
            response("HLS" to "http://a/hls"), station
        )!!

        assertNull(details.recordableUrl)
    }

    @Test
    fun `empty urls are ignored`() {
        val details = StationParser.parseStationDetails(
            response("HLS" to "", "MP3" to "http://a/mp3"), station
        )!!

        assertEquals("http://a/mp3", details.streamUrl)
    }

    @Test
    fun `an unsuccessful response yields null`() {
        assertNull(
            StationParser.parseStationDetails("""{"success":false}""", station)
        )
    }

    @Test
    fun `a response with no usable stream yields null`() {
        assertNull(StationParser.parseStationDetails(response(), station))
    }

    @Test
    fun `falls back to the scraped name when the api omits a title`() {
        val json = """
            {"success":true,"result":{
              "station":{"signal":"100.3 FM"},
              "streams":[{"mediaType":"MP3","url":"http://a/mp3"}]}}
        """.trimIndent()

        assertEquals("Kiss 100", StationParser.parseStationDetails(json, station)!!.title)
    }

    // endregion
}
