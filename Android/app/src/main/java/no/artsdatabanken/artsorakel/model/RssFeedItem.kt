package no.artsdatabanken.artsorakel.model

enum class RssCategory {
    DANGER,
    WARNING,
    INFO
}

data class RssFeedItem(
    val guid: String,
    val title: String,
    val description: String,
    val link: String? = null,
    val pubDate: String? = null,
    val language: String? = null,
    val categories: List<String> = emptyList(),
    val category: RssCategory = RssCategory.INFO
) {
    val isPermanent: Boolean
        get() = categories.any { it.equals("Permanent", ignoreCase = true) }
}