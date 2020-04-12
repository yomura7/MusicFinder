package sample

import com.google.gson.Gson
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.respondRedirect
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.sessions.*
import kotlinx.html.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigInteger
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.*

actual class Sample {
    actual fun checkMe() = 42
}

actual object Platform {
    actual val name: String = "JVM"
}

fun main() {
    val stateKey = "spotify_auth_state"
    var accessToken: String? = null

    embeddedServer(Netty, port = 8080, host = "127.0.0.1") {
        install(Sessions) {
            cookie<MySession>(stateKey)
        }

        routing {
            get("/") {
                val me = callAPI<Me>("/me", accessToken)
                val newReleases = callAPI<NewReleases>("/browse/new-releases?limit=3", accessToken)
                val albums = when(newReleases) {
                    null -> null
                    else -> newReleases.albums.items.map { it }
                }
                val status = when (accessToken) {
                    null -> "Not Logged in"
                    else -> "Logged in as ${me?.display_name}"
                }
                call.respondHtml {
                    head {
                        title("Music Finder from Spotify")
                    }
                    body {
                        h1 {
                            +"Music Finder from Spotify"
                        }
//                        +"${hello()} from Ktor. Check me value: ${Sample().checkMe()}"
                        div {
                            a {
                                href = "/login"
                                button {
                                    +"Login"
                                }
                            }
                        }
                        div {
                            +"status: $status"
                        }
                        h2 {
                            +"New Releases"
                        }
                        ul {
                            if (albums == null) {
                                return@ul
                            }
                            li {
                                +"${albums[0].artists.first().name} - ${albums[0].name}"
                            }
                            li {
                                +"${albums[1].artists.first().name} - ${albums[1].name}"
                            }
                        }
                        script(src = "/static/MusicFinder.js") {}
                    }
                }
            }
            get("/login") {
                val state = getRandomString(16)
                call.sessions.set(MySession(name = stateKey, value = state))

                call.respondRedirect {
                    protocol = URLProtocol.HTTPS
                    port = DEFAULT_PORT
                    host = "accounts.spotify.com"
                    path("authorize")
                    parameters["response_type"] = "code"
                    parameters["client_id"] = Constants.CLIENT_ID
//                    parameters["scope"] = "user-read-private user-read-email"
                    parameters["scope"] = "user-read-private"
                    parameters["redirect_uri"] = Constants.REDIRECT_URI
                    parameters["state"] = state
                }
            }
            get("/callback") {
                val code = call.request.queryParameters["code"]
                val state = call.request.queryParameters["state"]
                val storedState = call.sessions.get<MySession>()?.value

                if (code == null || state == null || state != storedState) {
                    throw Exception("invalid authorization code")
                }
                call.sessions.clear(stateKey)

                val res = OkHttpClient().newCall(Request.Builder().apply {
                    addHeader("Content-Type", "application/x-www-form-urlencoded")
                    val utf8 = StandardCharsets.UTF_8.toString()
                    val clientId = URLEncoder.encode(Constants.CLIENT_ID, utf8)
                    val clientSecret = URLEncoder.encode(Constants.CLIENT_SECRET, utf8)
                    val credentials = Base64.getEncoder()
                        .encodeToString("${clientId}:${clientSecret}".toByteArray())
                    addHeader("Authorization", "Basic $credentials")
                    val body = "code=$code&redirect_uri=${Constants.REDIRECT_URI}&grant_type=authorization_code".toRequestBody()
                    method("POST", body)
                    url("https://accounts.spotify.com/api/token")
                }.build()).execute()
                println("res: $res")
                if (res.code != 200) {
                    throw Exception("invalid token")
                    call.respondRedirect("/", false)
                }
                if (res.body == null) {
                    throw Exception("invalid token")
                    call.respondRedirect("/", false)
                }
                val json = res.body?.string()
                val resBody = Gson().fromJson(json, Token::class.java)
                accessToken = resBody.access_token
                call.respondRedirect("/", false)
            }
            get("/refresh_token") {
                // TODO
            }
            static("/static") {
                resource("MusicFinder.js")
            }
        }
    }.start(wait = true)
}

fun getRandomString(length: Int): String = BigInteger(160, SecureRandom()).toString(32)

inline fun <reified T> callAPI(path: String, accessToken: String?): T? {
    if (accessToken == null) {
        return null
    }
    println("<-- GET $path")
    val res = OkHttpClient().newCall(Request.Builder().apply {
        addHeader("Authorization", "Bearer $accessToken")
        method("GET", null)
        url("https://api.spotify.com/v1$path")
    }.build()).execute()
    if (res.body == null) {
        throw Exception("failed to call API")
    }
    println("--> GET $path ${res.code}")
    val json = res.body!!.string()
    println("$json")
    return Gson().fromJson(json, T::class.java)
}
