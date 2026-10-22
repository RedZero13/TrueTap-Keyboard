package com.truetap.keyboard.net

import org.json.JSONObject

/** GIPHY search/trending for GIFs and stickers. Needs a free API key from developers.giphy.com. */
class Giphy(private val apiKey: String) {
    data class Item(
        val id: String,
        val title: String,
        val previewUrl: String,
        val previewWidth: Int,
        val previewHeight: Int,
        val sendUrl: String,
        val pageUrl: String,
    )

    enum class Kind(val path: String) { GIFS("gifs"), STICKERS("stickers") }

    /** Blocking; call from a background thread. Empty [query] returns trending. */
    fun fetch(kind: Kind, query: String, offset: Int = 0, lang: String = "en"): List<Item> {
        require(apiKey.isNotBlank()) { "No GIPHY API key" }
        val base = "https://api.giphy.com/v1/${kind.path}"
        val url = if (query.isBlank()) {
            "$base/trending?api_key=${Net.enc(apiKey)}&limit=$PAGE&offset=$offset&rating=pg-13"
        } else {
            "$base/search?api_key=${Net.enc(apiKey)}&q=${Net.enc(query)}&limit=$PAGE&offset=$offset&rating=pg-13&lang=$lang"
        }
        val data = JSONObject(Net.getText(url)).getJSONArray("data")
        val out = ArrayList<Item>(data.length())
        for (i in 0 until data.length()) {
            val o = data.getJSONObject(i)
            val images = o.optJSONObject("images") ?: continue
            val preview = images.optJSONObject("fixed_width") ?: images.optJSONObject("fixed_width_downsampled") ?: continue
            val send = images.optJSONObject("downsized") ?: images.optJSONObject("original") ?: preview
            val previewUrl = preview.optString("url")
            val sendUrl = send.optString("url")
            if (previewUrl.isEmpty() || sendUrl.isEmpty()) continue
            out.add(
                Item(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    previewUrl = previewUrl,
                    previewWidth = preview.optString("width").toIntOrNull() ?: 200,
                    previewHeight = preview.optString("height").toIntOrNull() ?: 200,
                    sendUrl = sendUrl,
                    pageUrl = o.optString("url"),
                ),
            )
        }
        return out
    }

    companion object {
        const val PAGE = 30
    }
}
