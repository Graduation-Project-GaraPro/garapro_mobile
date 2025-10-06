package com.example.garapro.data.local

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistentCookieJar(context: Context) : CookieJar {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "cookie_prefs",
        Context.MODE_PRIVATE
    )

    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    init {
        // Load cookies từ SharedPreferences khi khởi tạo
        loadCookies()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host

        // Lưu cookies vào memory
        cookieStore[host] = cookies.toMutableList()

        // Lưu cookies vào SharedPreferences
        saveCookies(host, cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = cookieStore[host] ?: emptyList()

        // Lọc bỏ cookies đã hết hạn
        return cookies.filter { !it.expiresAt.isBefore(System.currentTimeMillis()) }
    }

    private fun saveCookies(host: String, cookies: List<Cookie>) {
        val editor = prefs.edit()
        val cookieStrings = cookies.map { serializeCookie(it) }
        editor.putStringSet("cookies_$host", cookieStrings.toSet())
        editor.apply()
    }

    private fun loadCookies() {
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("cookies_")) {
                val host = key.removePrefix("cookies_")
                val cookieStrings = value as? Set<String> ?: return@forEach

                val cookies = cookieStrings.mapNotNull { deserializeCookie(it) }
                cookieStore[host] = cookies.toMutableList()
            }
        }
    }

    fun clearCookies() {
        cookieStore.clear()
        prefs.edit().clear().apply()
    }

    private fun serializeCookie(cookie: Cookie): String {
        return "${cookie.name}=${cookie.value};" +
                "domain=${cookie.domain};" +
                "path=${cookie.path};" +
                "expires=${cookie.expiresAt};" +
                "secure=${cookie.secure};" +
                "httponly=${cookie.httpOnly}"
    }

    private fun deserializeCookie(cookieString: String): Cookie? {
        return try {
            val parts = cookieString.split(";").associate {
                val (key, value) = it.split("=", limit = 2)
                key.trim() to value.trim()
            }

            val name = parts.keys.first()
            val value = parts[name] ?: return null

            Cookie.Builder()
                .name(name)
                .value(value)
                .domain(parts["domain"] ?: return null)
                .path(parts["path"] ?: "/")
                .expiresAt(parts["expires"]?.toLongOrNull() ?: Long.MAX_VALUE)
                .apply {
                    if (parts["secure"] == "true") secure()
                    if (parts["httponly"] == "true") httpOnly()
                }
                .build()
        } catch (e: Exception) {
            null
        }
    }

    private fun Long.isBefore(currentTime: Long): Boolean {
        return this < currentTime
    }
}