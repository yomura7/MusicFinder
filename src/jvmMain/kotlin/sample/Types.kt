package sample

data class MySession(val name: String, val value: String)

data class Token(
    val access_token: String,
    val token_type: String,
    val expires_in: Int,
    val refresh_token: String,
    val scope: String
)

/**
 *  GET /v1/me
 */
data class Me(
    val country: String,
    val display_name: String?,
    val email: String?,
    val external_urls: Unit?,
    val href: String,
    val id: String,
    val images: Array<ImageObject>,
    val product: String,
    val type: String,
    val uri: String
)
 data class ImageObject (
    val height: String?,
    val url: String?,
    val width: String?
)

/**
 *  GET /v1/browse/new-releases
 */
data class NewReleases(
    val albums: NewReleasesPagingObject,
    val message: String?
)
open class NewReleasesPagingObject(
    val href: String,
    val items: Array<Albums>,
    val limit: Int,
    val next: String,
    val offset: Int,
    val previous: Int?,
    val total: Int
)
data class Albums(
    val album_type: String,
    val artists: Array<Artist>,
    val available_markets: Array<String>,
    val external_urls: ExternalUrls,
    val href: String,
    val id: String,
    val images: Array<ImageObject>,
    val name: String,
    val release_date: String,
    val release_date_precision: String,
    val total_tracks: Int,
    val type: String,
    val uri: String
)
data class Artist(
    val external_urls: ExternalUrls,
    val href: String,
    val id: String,
    val name: String,
    val type: String,
    val uri: String
)
data class ExternalUrls(
    val spotify: String
)
