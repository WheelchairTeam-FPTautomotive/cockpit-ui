package com.wheelchair.cockpit.api

import android.util.Log
import com.wheelchair.cockpit.dev.DevSettings
import com.wheelchair.cockpit.dev.DevSettingsStore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

// --- START MODIFICATION ---
// Rewrite scheme/host/port to DevSettings.effectiveBaseUrl (stored URL always wins).
// Retrofit singleton keeps DEFAULT_BASE_URL only as the compile-time seed.
class DynamicHostInterceptor(
    private val settingsProvider: () -> DevSettings
) : Interceptor {

    constructor(store: DevSettingsStore) : this({ store.current() })

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val settings = settingsProvider()
        val target = settings.effectiveBaseUrl.toHttpUrlOrNull()
        if (target == null) {
            Log.w(TAG, "Invalid effectiveBaseUrl=${settings.effectiveBaseUrl}; using request as-is")
            return chain.proceed(request)
        }

        if (
            request.url.scheme == target.scheme &&
            request.url.host == target.host &&
            request.url.port == target.port
        ) {
            return chain.proceed(request)
        }

        val newUrl = request.url.newBuilder()
            .scheme(target.scheme)
            .host(target.host)
            .port(target.port)
            .build()

        Log.d(
            TAG,
            "Host rewrite ${request.url.host}:${request.url.port} → ${newUrl.host}:${newUrl.port}"
        )
        return chain.proceed(request.newBuilder().url(newUrl).build())
    }

    companion object {
        private const val TAG = "CockpitUI"
    }
}
// --- END MODIFICATION ---
