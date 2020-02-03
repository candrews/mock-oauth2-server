package no.nav.security.mock.oauth2

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.TokenRequest
import mu.KotlinLogging
import no.nav.security.mock.callback.TokenCallback
import no.nav.security.mock.extensions.clientIdAsString
import okhttp3.HttpUrl
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Date
import java.util.UUID

private val log = KotlinLogging.logger {}

open class OAuth2TokenProvider {
    private val jwkSet: JWKSet
    private val rsaKey: RSAKey

    init {
        jwkSet = generateJWKSet(DEFAULT_KEYID)
        rsaKey = jwkSet.getKeyByKeyId(DEFAULT_KEYID) as RSAKey
    }

    fun publicJwkSet(): JWKSet {
        return jwkSet.toPublicJWKSet()
    }

    fun idToken(
        tokenRequest: TokenRequest,
        issuerUrl: HttpUrl,
        nonce: String?,
        tokenCallback: TokenCallback
    ): SignedJWT {
        return createSignedJWT(
            defaultClaims(
                issuerUrl,
                tokenCallback.subject(tokenRequest),
                tokenRequest.clientIdAsString(),
                nonce,
                tokenCallback.addClaims(tokenRequest)
            ).build()
        )
    }

    fun accessToken(
        tokenRequest: TokenRequest,
        issuerUrl: HttpUrl,
        audience: String,
        nonce: String?,
        tokenCallback: TokenCallback
    ): SignedJWT {
        return createSignedJWT(
            defaultClaims(
                issuerUrl,
                tokenCallback.subject(tokenRequest),
                audience,
                nonce,
                tokenCallback.addClaims(tokenRequest)
            ).build()
        )
    }

    fun createSignedJWT(claimsSet: JWTClaimsSet): SignedJWT {
        val header = JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(rsaKey.keyID)
            .type(JOSEObjectType.JWT)
        val signedJWT = SignedJWT(header.build(), claimsSet)
        val signer = RSASSASigner(rsaKey.toPrivateKey())
        signedJWT.sign(signer)
        return signedJWT
    }

    private fun defaultClaims(
        issuerUrl: HttpUrl,
        subject: String,
        audience: String,
        nonce: String?,
        additionalClaims: Map<String, Any>
    ): JWTClaimsSet.Builder {
        val now = Instant.now()
        val jwtClaimsSetBuilder = JWTClaimsSet.Builder()
            .subject(subject)
            .audience(audience)
            .issuer(issuerUrl.toString())
            .issueTime(Date.from(now))
            .notBeforeTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(3600)))
            .jwtID(UUID.randomUUID().toString())

        if (nonce != null) {
            jwtClaimsSetBuilder.claim("nonce", nonce)
        }
        additionalClaims.forEach {
            jwtClaimsSetBuilder.claim(it.key, it.value)
        }
        return jwtClaimsSetBuilder
    }

    companion object {
        private const val DEFAULT_KEYID = "mock-oauth2-server-key"
        private fun generateJWKSet(keyId: String): JWKSet {
            return JWKSet(createJWK(keyId, generateKeyPair()))
        }

        private fun generateKeyPair(): KeyPair {
            return try {
                val gen = KeyPairGenerator.getInstance("RSA")
                gen.initialize(2048)
                gen.generateKeyPair()
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException(e)
            }
        }

        private fun createJWK(keyID: String, keyPair: KeyPair): RSAKey {
            return RSAKey.Builder(keyPair.public as RSAPublicKey)
                .privateKey(keyPair.private as RSAPrivateKey)
                .keyID(keyID)
                .build()
        }
    }
}