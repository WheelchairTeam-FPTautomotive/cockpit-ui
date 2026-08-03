package com.wheelchair.cockpit.api

import com.wheelchair.cockpit.dev.DevSettings
import com.wheelchair.cockpit.dev.DevSettingsStore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

// --- START MODIFICATION ---
// Rewrites scheme/host/port from DevSettings when developer mode is enabled.
// Keeps Retrofit/OkHttp singleton intact (no client rebuild on URL change).
class DynamicHostInterceptor(
    private val settingsProvider: () -> DevSettings
) : Interceptor {

    constructor(store: DevSettingsStore) : this({ store.current() })

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val settings = settingsProvider()
        if (!settings.developerModeEnabled) {
            return chain.proceed(request)
        }

        val customBaseUrl = settings.effectiveBaseUrl.toHttpUrlOrNull()
            ?: return chain.proceed(request)

        val newUrl = request.url.newBuilder()
            .scheme(customBaseUrl.scheme)
            .host(customBaseUrl.host)
            .port(customBaseUrl.port)
            .build()

        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}
// --- END MODIFICATION ---
