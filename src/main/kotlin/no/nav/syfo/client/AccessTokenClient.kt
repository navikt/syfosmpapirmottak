package no.nav.syfo.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nav.syfo.log

class AccessTokenClient(
    private val aadAccessTokenUrl: String,
    private val clientId: String,
    private val clientSecret: String,
    private val httpClient: HttpClient
) {
    private val mutex = Mutex()

    @Volatile
    private var tokenMap = HashMap<String, AadAccessToken>()

    suspend fun hentAccessToken(resource: String): String {
        val omToMinutter = Instant.now().plusSeconds(120L)
        return mutex.withLock {
            (tokenMap[resource]
                    ?.takeUnless { it.expires_on.isBefore(omToMinutter) }
                    ?: run {
                        log.info("Henter nytt token fra Azure AD")
                        val response: AadAccessToken = httpClient.post(aadAccessTokenUrl) {
                            accept(ContentType.Application.Json)
                            method = HttpMethod.Post
                            body = FormDataContent(Parameters.build {
                                append("client_id", clientId)
                                append("resource", resource)
                                append("grant_type", "client_credentials")
                                append("client_secret", clientSecret)
                            })
                        }
                        tokenMap[resource] = response
                        log.debug("Har hentet accesstoken")
                        return@run response
                    }).access_token
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AadAccessToken(
    val access_token: String,
    val expires_on: Instant
)
