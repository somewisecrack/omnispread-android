package com.example.omnispread.data

import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP setup. Some networks advertise IPv6 but can't route it to Google/Yahoo; OkHttp then
 * waits a full connect timeout per request before trying IPv4. Try IPv4 addresses first.
 */
object Net {
    private val ipv4First = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            Dns.SYSTEM.lookup(hostname).sortedBy { if (it is Inet4Address) 0 else 1 }
    }

    fun client(connectSecs: Long = 10, readSecs: Long = 30): OkHttpClient = OkHttpClient.Builder()
        .dns(ipv4First)
        .connectTimeout(connectSecs, TimeUnit.SECONDS)
        .readTimeout(readSecs, TimeUnit.SECONDS)
        .build()
}
