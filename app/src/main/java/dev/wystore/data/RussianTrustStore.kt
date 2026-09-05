package dev.wystore.data

import android.content.Context
import dev.wystore.R
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object RussianTrustStore {
    @Volatile
    private var cachedTrustManager: X509TrustManager? = null
    @Volatile
    private var cachedSslContext: SSLContext? = null

    fun createClient(
        context: Context? = null,
        connectTimeoutSeconds: Long = 20,
        readTimeoutSeconds: Long = 90
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)

        if (context != null) {
            runCatching {
                val (sslContext, trustManager) = getSslContextAndTrustManager(context)
                builder.sslSocketFactory(sslContext.socketFactory, trustManager)
            }
        } else {
            cachedSslContext?.let { sslContext ->
                cachedTrustManager?.let { trustManager ->
                    builder.sslSocketFactory(sslContext.socketFactory, trustManager)
                }
            }
        }

        return builder.build()
    }

    @Synchronized
    fun getSslContextAndTrustManager(context: Context): Pair<SSLContext, X509TrustManager> {
        cachedSslContext?.let { sslCtx ->
            cachedTrustManager?.let { tm -> return sslCtx to tm }
        }

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
        }

        // 1. Add default system root certificates
        runCatching {
            val systemTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(null as KeyStore?)
            }
            val systemTm = systemTmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
            systemTm?.acceptedIssuers?.forEachIndexed { index, cert ->
                keyStore.setCertificateEntry("sys_$index", cert)
            }
        }

        // 2. Add bundled Russian CA certificates
        val certFactory = CertificateFactory.getInstance("X.509")
        listOf(R.raw.russian_trusted_root_ca, R.raw.russian_trusted_sub_ca).forEachIndexed { index, resId ->
            runCatching {
                context.resources.openRawResource(resId).use { stream ->
                    val cert = certFactory.generateCertificate(stream) as? X509Certificate
                    if (cert != null) {
                        keyStore.setCertificateEntry("russian_ca_$index", cert)
                    }
                }
            }
        }

        val customTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore)
        }
        val customTrustManager = customTmf.trustManagers.filterIsInstance<X509TrustManager>().first()
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(customTrustManager), null)
        }

        cachedTrustManager = customTrustManager
        cachedSslContext = sslContext
        return sslContext to customTrustManager
    }
}
