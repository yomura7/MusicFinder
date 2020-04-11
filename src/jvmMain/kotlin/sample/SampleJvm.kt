package sample

import io.ktor.application.*
import io.ktor.features.AutoHeadResponse
import io.ktor.features.AutoHeadResponse.install
import io.ktor.html.*
import io.ktor.http.DEFAULT_PORT
import io.ktor.http.URLProtocol
import io.ktor.http.content.*
import io.ktor.response.respondRedirect
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.sessions.Sessions
import io.ktor.sessions.cookie
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import kotlinx.html.*
import java.io.*
import java.math.BigInteger
import java.security.SecureRandom

actual class Sample {
    actual fun checkMe() = 42
}

actual object Platform {
    actual val name: String = "JVM"
}

data class MySession(val name: String, val value: String)

fun main() {
    val stateKey = "spotify_auth_state"
    embeddedServer(Netty, port = 8080, host = "127.0.0.1") {
        install(Sessions) {
            cookie<MySession>(stateKey)
        }
        routing {
            get("/") {
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
                            +"title: "
                        }
                        script(src = "/static/MusicFinder.js") {}
                    }
                }
            }
            get("/login") {
                val state = getRandomString(16)
                call.sessions.set(MySession(name = stateKey, value = state))

                println("state: $state")

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
                val storedState = call.sessions.get(stateKey) as? String

                if (code == null || state == null || state != storedState) {
                    throw Exception("invalid authorization code")
                }
                // TODO
            }
            static("/static") {
                resource("MusicFinder.js")
            }
        }
    }.start(wait = true)
}
fun getRandomString(length: Int): String = BigInteger(length, SecureRandom()).toString(32)

