package app.lazydex.scraper.source

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SourceRegistry(
    val builtInSources: List<Source>
) {
    private val _dynamicSources = MutableStateFlow<List<Source>>(emptyList())
    val dynamicSources: StateFlow<List<Source>> = _dynamicSources.asStateFlow()

    private val _sourcesFlow = MutableStateFlow<List<Source>>(builtInSources)
    val sourcesFlow: StateFlow<List<Source>> = _sourcesFlow.asStateFlow()

    val sources: List<Source>
        get() = _sourcesFlow.value

    fun registerDynamicSources(dynamicSources: List<Source>) {
        _dynamicSources.value = dynamicSources
        _sourcesFlow.value = builtInSources + dynamicSources
    }

    fun findSourceForUrl(url: String): Source? {
        val specificSource = sources.firstOrNull { it !is GenericSource && it.baseUrl.isNotBlank() && it.urlPattern.matches(url) }
        if (specificSource != null) return specificSource

        val nonGenericFallback = sources.firstOrNull { it !is GenericSource && it.urlPattern.matches(url) }
        if (nonGenericFallback != null) return nonGenericFallback

        return sources.firstOrNull { it is GenericSource } ?: sources.firstOrNull()
    }

    fun getSearchableSources(): List<Source> {
        return sources.filter { it.isSearchable }
    }

    suspend fun scrape(url: String): ScrapedMetadata? {
        val source = findSourceForUrl(url) ?: return null
        return source.scrape(url)
    }
}
