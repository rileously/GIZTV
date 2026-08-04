package com.example.auroratv.link

import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * The port a television listens on unless something has already taken it.
 *
 * Fixed on purpose. A phone that has been told an address can then reach the television with the
 * address alone, which is what makes typing one in — or finding one by sweeping the network —
 * possible at all.
 */
internal const val LINK_DEFAULT_PORT = 47788

/** How long a single address is given to answer during a sweep. */
private const val SCAN_TIMEOUT_MS = 250

/** Addresses tried at once. Enough to sweep a subnet in seconds, few enough to not drown Wi-Fi. */
private const val SCAN_BATCH = 32

/**
 * This device's own addresses on whatever it is actually connected by.
 *
 * A television shows these so a phone can be told where to look when nothing else will say. The
 * tethering interface counts: a television on a phone's hotspot is on a perfectly ordinary network,
 * it is simply one the phone is the router for.
 */
internal fun localIpAddresses(): List<String> =
  runCatching {
      NetworkInterface.getNetworkInterfaces()
        .toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .filterNot { it.isLoopbackAddress }
        .mapNotNull { it.hostAddress }
        .distinct()
    }
    .getOrDefault(emptyList())

/** The `192.168.43` part of every address this device holds, which is the range to sweep. */
internal fun localSubnets(): List<String> =
  localIpAddresses().mapNotNull { address ->
    address.substringBeforeLast('.', "").takeIf { it.count { c -> c == '.' } == 2 }
  }
    .distinct()

/**
 * Looks for televisions by trying every address on this network in turn.
 *
 * The way in when the usual announcement cannot be heard: a phone's own hotspot carries no multicast
 * to speak of, and a guest network often filters it, so nothing is ever announced. Sweeping a single
 * subnet is crude but it is only 254 addresses and it finds what is actually there.
 *
 * Nothing is said down the sockets it opens. A television only asks the viewer to approve a phone
 * once that phone introduces itself, so a sweep that merely knocks leaves no codes on screen.
 */
internal suspend fun scanForTelevisions(ports: List<Int>): List<LinkTarget> =
  withContext(Dispatchers.IO) {
    val candidates = localSubnets().flatMap { prefix -> (1..254).map { "$prefix.$it" } }
    val mine = localIpAddresses().toSet()
    candidates
      .filterNot(mine::contains)
      .chunked(SCAN_BATCH)
      .flatMap { batch ->
        coroutineScope {
          batch
            .map { host -> async { ports.firstOrNull { port -> knocks(host, port) }?.let { host to it } } }
            .awaitAll()
        }
      }
      .filterNotNull()
      .map { (host, port) -> LinkTarget(name = "GIZTV at $host", host = host, port = port) }
  }

private fun knocks(host: String, port: Int): Boolean =
  runCatching {
      Socket().use { socket ->
        socket.connect(InetSocketAddress(host, port), SCAN_TIMEOUT_MS)
        true
      }
    }
    .getOrDefault(false)
