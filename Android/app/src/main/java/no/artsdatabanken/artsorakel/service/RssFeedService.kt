package no.artsdatabanken.artsorakel.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.artsdatabanken.artsorakel.BuildConfig
import no.artsdatabanken.artsorakel.model.RssFeedItem
import no.artsdatabanken.artsorakel.model.RssCategory
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RssFeedService @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("rss_prefs", Context.MODE_PRIVATE)
    }

    private companion object {
        const val KEY_DISMISSED_GUIDS = "dismissed_guids"
        const val KEY_FEED_FETCHED_SESSION = "feed_fetched_session"
        const val KEY_SESSION_ID = "session_id"
    }

    private val sessionId: String by lazy {
        val savedSessionId = prefs.getString(KEY_SESSION_ID, null)
        val currentTime = System.currentTimeMillis().toString()
        if (savedSessionId == null) {
            prefs.edit().putString(KEY_SESSION_ID, currentTime).apply()
            currentTime
        } else {
            savedSessionId
        }
    }

    private var cachedAllItems: List<RssFeedItem>? = null

    suspend fun fetchFilteredRssItem(): RssFeedItem? = withContext(Dispatchers.IO) {
        try {
            val fetchedSession = prefs.getString(KEY_FEED_FETCHED_SESSION, null)
            val allItems = if (fetchedSession == sessionId && cachedAllItems != null) {
                cachedAllItems!!
            } else {
                val items = fetchAllRssItems()
                cachedAllItems = items
                prefs.edit().putString(KEY_FEED_FETCHED_SESSION, sessionId).apply()
                cleanupOldDismissedGuids(items.map { it.guid })
                items
            }

            val dismissedGuids = getDismissedGuids()

            val filteredItems = allItems.filter { item ->
                passesAllFilters(item) && !isDismissed(item, dismissedGuids)
            }

            filteredItems.firstOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun refilterCachedItems(): RssFeedItem? {
        val allItems = cachedAllItems ?: return null

        val dismissedGuids = getDismissedGuids()

        val filteredItems = allItems.filter { item ->
            passesAllFilters(item) && !isDismissed(item, dismissedGuids)
        }

        return filteredItems.firstOrNull()
    }

    fun resetSessionOnAppStart() {
        val currentTime = System.currentTimeMillis().toString()
        prefs.edit().putString(KEY_SESSION_ID, currentTime).apply()
        cachedAllItems = null
    }

    private suspend fun fetchAllRssItems(): List<RssFeedItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<RssFeedItem>()

        try {
            val url = URL(BuildConfig.RSS_FEED_URL)
            val connection = url.openConnection()
            connection.connect()
            val inputStream = connection.getInputStream()

            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            var currentGuid: String? = null
            var currentTitle: String? = null
            var currentDescription: String? = null
            var currentLink: String? = null
            var currentPubDate: String? = null
            var currentLanguage: String? = null
            val currentCategories = mutableListOf<String>()
            var currentRssCategory: RssCategory = RssCategory.INFO
            var insideItem = false
            var insideChannel = false
            var channelLanguage: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name?.lowercase()) {
                            "channel" -> insideChannel = true
                            "language" -> {
                                if (insideChannel && !insideItem) {
                                    channelLanguage = parser.nextText()
                                }
                            }
                            "item" -> {
                                insideItem = true
                                currentLanguage = channelLanguage
                                currentCategories.clear()
                                currentRssCategory = RssCategory.INFO
                            }
                            "guid" -> if (insideItem) currentGuid = parser.nextText()
                            "title" -> if (insideItem) currentTitle = parser.nextText()
                            "description" -> if (insideItem) currentDescription = parser.nextText()
                            "link" -> if (insideItem) currentLink = parser.nextText()
                            "pubdate" -> if (insideItem) currentPubDate = parser.nextText()
                            "category" -> if (insideItem) {
                                val categoryText = parser.nextText()
                                when (categoryText.uppercase()) {
                                    "DANGER" -> currentRssCategory = RssCategory.DANGER
                                    "WARNING" -> currentRssCategory = RssCategory.WARNING
                                    "INFO" -> currentRssCategory = RssCategory.INFO
                                    else -> currentCategories.add(categoryText)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name?.lowercase()) {
                            "channel" -> {
                                insideChannel = false
                                channelLanguage = null
                            }
                            "item" -> {
                                if (insideItem && currentGuid != null && currentTitle != null && currentDescription != null) {
                                    items.add(
                                        RssFeedItem(
                                            guid = currentGuid,
                                            title = currentTitle,
                                            description = cleanDescription(currentDescription),
                                            link = currentLink,
                                            pubDate = currentPubDate,
                                            language = currentLanguage,
                                            categories = currentCategories.toList(),
                                            category = currentRssCategory
                                        )
                                    )
                                }
                                insideItem = false
                                currentGuid = null
                                currentTitle = null
                                currentDescription = null
                                currentLink = null
                                currentPubDate = null
                                currentLanguage = null
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        items
    }

    private fun passesAllFilters(item: RssFeedItem): Boolean {
        return passesLanguageFilter(item) &&
               passesVersionFilter(item) &&
               passesPlatformFilter(item)
    }

    private fun passesLanguageFilter(item: RssFeedItem): Boolean {
        if (item.language == null) return true

        val currentLocale = Locale.getDefault()
        val currentLanguage = currentLocale.language

        return item.language == currentLanguage ||
               (item.language == "nb" && currentLanguage == "no") ||
               (item.language == "no" && currentLanguage == "nb")
    }

    private fun passesVersionFilter(item: RssFeedItem): Boolean {
        val versionRequirements = item.categories.filter { it.startsWith("Version:") }
        if (versionRequirements.isEmpty()) return true

        val currentVersion = BuildConfig.VERSION_NAME

        return versionRequirements.all { requirement ->
            val versionPart = requirement.substring("Version:".length).trim()
            checkVersionRequirement(currentVersion, versionPart)
        }
    }

    private fun checkVersionRequirement(currentVersion: String, requirement: String): Boolean {
        val operators = listOf("<=", ">=", "<", ">", "=")
        var operator = "="
        var versionToCompare = requirement

        for (op in operators) {
            if (requirement.startsWith(op)) {
                operator = op
                versionToCompare = requirement.substring(op.length).trim()
                break
            }
        }

        if (!versionToCompare.contains(Regex("^\\d+(\\.\\d+)*$"))) {
            versionToCompare = requirement
            operator = "="
        }

        val comparison = compareVersions(currentVersion, versionToCompare)

        return when (operator) {
            "<" -> comparison < 0
            "<=" -> comparison <= 0
            ">" -> comparison > 0
            ">=" -> comparison >= 0
            "=" -> comparison == 0
            else -> comparison == 0
        }
    }

    private fun compareVersions(version1: String, version2: String): Int {
        val parts1 = version1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = version2.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLength) {
            val part1 = if (i < parts1.size) parts1[i] else 0
            val part2 = if (i < parts2.size) parts2[i] else 0

            if (part1 < part2) return -1
            if (part1 > part2) return 1
        }

        return 0
    }

    private fun passesPlatformFilter(item: RssFeedItem): Boolean {
        val platformRequirements = item.categories.filter { it.startsWith("Platform:") }
        if (platformRequirements.isEmpty()) return true

        val currentPlatform = "Android"

        return platformRequirements.any { requirement ->
            val platform = requirement.substring("Platform:".length).trim()
            platform.equals(currentPlatform, ignoreCase = true)
        }
    }

    private fun isDismissed(item: RssFeedItem, dismissedGuids: Set<String>): Boolean {
        // Permanent items are never considered dismissed since they can't be dismissed
        if (item.isPermanent) return false

        return dismissedGuids.contains(item.guid)
    }

    fun dismissItem(item: RssFeedItem) {
        // Permanent items cannot be dismissed
        if (item.isPermanent) return

        val dismissedGuids = getDismissedGuids().toMutableSet()
        dismissedGuids.add(item.guid)
        saveDismissedGuids(dismissedGuids)
    }

    private fun getDismissedGuids(): Set<String> {
        val guidsString = prefs.getString(KEY_DISMISSED_GUIDS, "") ?: ""
        return if (guidsString.isEmpty()) {
            emptySet()
        } else {
            guidsString.split(",").toSet()
        }
    }

    private fun saveDismissedGuids(guids: Set<String>) {
        prefs.edit().putString(KEY_DISMISSED_GUIDS, guids.joinToString(",")).apply()
    }

    private fun cleanupOldDismissedGuids(currentGuids: List<String>) {
        val dismissedGuids = getDismissedGuids()
        val currentGuidsSet = currentGuids.toSet()
        val cleanedGuids = dismissedGuids.intersect(currentGuidsSet)

        if (cleanedGuids.size < dismissedGuids.size) {
            saveDismissedGuids(cleanedGuids)
        }
    }

    private fun cleanDescription(description: String): String {
        return description
            .replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
    }
}