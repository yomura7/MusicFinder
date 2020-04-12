package sample

import com.google.gson.Gson
import io.ktor.application.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.feature
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.response.HttpResponse
import io.ktor.client.utils.buildHeaders
import io.ktor.features.AutoHeadResponse
import io.ktor.features.AutoHeadResponse.install
import io.ktor.features.DefaultHeaders
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
import okhttp3.Response
import java.io.*
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

data class MySession(val name: String, val value: String)
data class Token(
    val access_token: String,
    val token_type: String,
    val expires_in: Int,
    val refresh_token: String,
    val scope: String
)
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

fun main() {
    val stateKey = "spotify_auth_state"
    embeddedServer(Netty, port = 8080, host = "127.0.0.1") {
        install(Sessions) {
            cookie<MySession>(stateKey)
        }

        routing {
            get("/") {
                val accessToken = call.request.queryParameters["access_token"]
                if (accessToken != null) {
                    println("Logged-In!")
                }
                call.respondHtml {
                    head {
                        title("Music Finder from Spotify")
                    }
                    body {
                        +"${hello()} from Ktor. Check me value: ${Sample().checkMe()}"
                        div {
                            id = "js-response"
                            +"Loading..."
                        }
                        div {
                            a {
                                href = "/login"
                                button {
                                    id = "login"
                                    +"Login"
                                }
                            }
                        }
                        div {
                            id = "status"
                            +"status: "
                        }
                        div {
                            id = "title"
                            +"title:"
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
                    parameters["client_id"] = Constant.CLIENT_ID
//                    parameters["scope"] = "user-read-private user-read-email"
                    parameters["scope"] = "user-read-private"
                    parameters["redirect_uri"] = Constant.REDIRECT_URI
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
                    val clientId = URLEncoder.encode(Constant.CLIENT_ID, utf8)
                    val clientSecret = URLEncoder.encode(Constant.CLIENT_SECRET, utf8)
                    val credentials = Base64.getEncoder()
                        .encodeToString("${clientId}:${clientSecret}".toByteArray())
                    addHeader("Authorization", "Basic $credentials")
                    val body = "code=$code&redirect_uri=${Constant.REDIRECT_URI}&grant_type=authorization_code".toRequestBody()
                    method("POST", body)
                    url("https://accounts.spotify.com/api/token")
                }.build()).execute()
                println("res: $res")
                if (res.code != 200) {
                    throw Exception("invalid token")
                    // TODO: redirect
                }
                if (res.body == null) {
                    throw Exception("invalid token")
                    // TODO: redirect
                }
                val json = res.body?.string()
                val resBody = Gson().fromJson(json, Token::class.java)
                println("body: $resBody")
                val accessToken = resBody.access_token

                val me = callAPI<Me>("/me", accessToken)
                println(me.display_name)

                call.respondRedirect("/", false)
                // TODO: send paremeter (token)
            }
            static("/static") {
                resource("MusicFinder.js")
            }
        }
    }.start(wait = true)
}

fun getRandomString(length: Int): String = BigInteger(160, SecureRandom()).toString(32)

inline fun <reified T> callAPI(path: String, accessToken: String): T {
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
    val resBody = Gson().fromJson(json, T::class.java)
    return resBody
}
