package dev.wystore.data

interface StoreSource {
    suspend fun search(query: String, page: Int = 1): SearchPage
    suspend fun details(packageName: String, includeReviews: Boolean = true): StoreApp
    suspend fun resolveArtifacts(app: StoreApp): List<DownloadArtifact>

    /** Sections offered by the source's catalogue, in the order the source lists them. */
    suspend fun categories(): List<StoreCategory>

    /**
     * One page of a catalog section. A blank [slug] means the source's own landing selection.
     */
    suspend fun catalog(slug: String, page: Int = 1): CatalogPage
}
