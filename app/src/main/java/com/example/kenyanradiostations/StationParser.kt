package com.example.kenyanradiostations

import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * Pure parsing for the two upstream sources. Extracted from MainActivity so it
 * can be unit tested without a device: both functions take a String and return
 * plain data.
 */
object StationParser {

    /** Parses the radio.or.ke landing page into the station catalogue. */
    fun parseStations(html: String): List<RadioStation> =
        Jsoup.parse(html)
            .select("li[class^='item-']")
            .mapNotNull { element ->
                val link = element.selectFirst("a") ?: return@mapNotNull null
                val image = element.selectFirst("img") ?: return@mapNotNull null
                val id = link.attr("href").substringAfterLast('#', "")
                val name = link.attr("title").trim()
                if (id.isEmpty() || name.isEmpty()) {
                    null
                } else {
                    RadioStation(id = id, name = name, logoUrl = image.attr("src"))
                }
            }
            .distinctBy { it.id }

    /**
     * Parses the stream API response.
     *
     * [StationDetails.streamUrl] follows the playback preference HLS > AAC >
     * MP3, while every URL is kept so recording can invert that order.
     */
    fun parseStationDetails(json: String, station: RadioStation): StationDetails? {
        val root = JSONObject(json)
        if (!root.optBoolean("success", false)) return null

        val result = root.optJSONObject("result") ?: return null
        val stationInfo = result.optJSONObject("station")
        val streams = result.optJSONArray("streams") ?: return null

        var hlsUrl: String? = null
        var aacUrl: String? = null
        var mp3Url: String? = null

        for (index in 0 until streams.length()) {
            val stream = streams.optJSONObject(index) ?: continue
            val url = stream.optString("url")
            if (url.isEmpty()) continue
            when (stream.optString("mediaType")) {
                "HLS" -> hlsUrl = hlsUrl ?: url
                "AAC" -> aacUrl = aacUrl ?: url
                "MP3" -> mp3Url = mp3Url ?: url
            }
        }

        val streamUrl = hlsUrl ?: aacUrl ?: mp3Url ?: return null

        return StationDetails(
            title = stationInfo?.optString("title").orEmptyOr(station.name),
            signal = stationInfo?.optString("signal").orEmpty(),
            logoUrl = station.logoUrl,
            streamUrl = streamUrl,
            mp3Url = mp3Url,
            aacUrl = aacUrl,
            hlsUrl = hlsUrl
        )
    }

    private fun String?.orEmptyOr(fallback: String): String =
        if (isNullOrBlank()) fallback else this
}
