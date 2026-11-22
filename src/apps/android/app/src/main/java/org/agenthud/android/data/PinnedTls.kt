package org.agenthud.android.data

import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Builds an OkHttpClient that trusts ONLY the desktop app's self-signed TLS
 * certificate matching the pinned SHA-256 hex digest of its DER bytes.
 */
object PinnedTls {
    fun clientFor(
        certificateSha256: String,
        readTimeoutSeconds: Long = 20L,
        hostIpFallback: Pair<String, String>? = null,
    ): OkHttpClient {
        val expected = certificateSha256.trim().lowercase()
        require(expected.length == 64) { "Invalid SHA-256 certificate pin" }

        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                throw CertificateException("Client auth not supported")
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val leaf = chain?.firstOrNull()
                    ?: throw CertificateException("Empty server certificate chain")
                val actual = sha256Hex(leaf.encoded)
                if (!constantTimeEquals(actual, expected)) {
                    throw CertificateException(
                        "Certificate SHA-256 mismatch: expected $expected, got $actual",
                    )
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }

        // The LAN server presents a self-signed certificate for its device identity
        // across dynamic LAN/Tailscale IP addresses; trust is anchored strictly by
        // the pinned leaf certificate SHA-256 above.
        val verifier = HostnameVerifier { _, session ->
            val cert = session.peerCertificates.firstOrNull() as? X509Certificate ?: return@HostnameVerifier false
            constantTimeEquals(sha256Hex(cert.encoded), expected)
        }

        val builder = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier(verifier)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (hostIpFallback != null && hostIpFallback.first.isNotBlank() && hostIpFallback.second.isNotBlank()) {
            val (targetHost, fallbackIp) = hostIpFallback
            builder.dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    return try {
                        Dns.SYSTEM.lookup(hostname)
                    } catch (e: Exception) {
                        if (hostname.equals(targetHost, ignoreCase = true)) {
                            listOf(InetAddress.getByName(fallbackIp))
                        } else {
                            throw e
                        }
                    }
                }
            })
        }

        return builder.build()
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(HEX[(b.toInt() ushr 4) and 0x0F])
            sb.append(HEX[b.toInt() and 0x0F])
        }
        return sb.toString()
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].code xor b[i].code)
        }
        return diff == 0
    }

    private val HEX = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
}
