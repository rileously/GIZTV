package com.example.auroratv.link

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val LOG_TAG = "GizTvLink"

/** Long enough to read a number off a television and type it, short enough to not sit there. */
private const val PAIRING_WINDOW_MS = 120_000L

/** How often the phone is told where playback has got to. */
private const val STATE_INTERVAL_MS = 1_000L

/** A phone that connects and then says nothing is not left holding a socket open. */
private const val HANDSHAKE_TIMEOUT_MS = 30_000

/**
 * The television's end: one socket, any number of phones, and a code before any of them can do
 * anything.
 *
 * Only runs on a television. A phone is the thing holding the remote, not the thing being pointed
 * at, and an app that listened on both would let one phone drive another.
 */
internal class LinkServer(private val context: Context) {
  private val store = LinkStore(context)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var serverSocket: ServerSocket? = null
  private var registration: NsdManager.RegistrationListener? = null
  private var job: Job? = null

  private val clients = mutableSetOf<LinkConnection>()

  private val _address = MutableStateFlow<String?>(null)

  /** Where this television can be reached, shown beside the code for a phone that cannot find it. */
  val address: StateFlow<String?> = _address.asStateFlow()

  private val _pairingCode = MutableStateFlow<String?>(null)

  /** The code the television is showing, or null when it is not asking for one. */
  val pairingCode: StateFlow<String?> = _pairingCode.asStateFlow()

  @Volatile private var pendingCode: String? = null
  @Volatile private var pendingCodeExpiresAt = 0L

  fun start() {
    if (job != null) return
    job =
      scope.launch {
        val socket = openServerSocket()
          ?: run {
            Log.w(LOG_TAG, "The remote could not take a port")
            return@launch
          }
        serverSocket = socket
        store.rememberPort(socket.localPort)
        _address.value = localIpAddresses().firstOrNull()?.let { "$it:${socket.localPort}" }
        advertise(socket.localPort)
        Log.i(LOG_TAG, "Remote listening on ${socket.localPort}")
        while (isActive && !socket.isClosed) {
          // One refused handshake must not end the remote for the rest of the evening: a failed
          // accept is only fatal when the socket itself has gone, and otherwise the next phone to
          // knock deserves an answer.
          val client =
            runCatching { socket.accept() }
              .onFailure { if (socket.isClosed) return@launch }
              .getOrNull() ?: continue
          launch { serve(client) }
        }
      }
    scope.launch { broadcastState() }
  }

  /**
   * Takes a port a phone has a chance of guessing.
   *
   * A phone remembers where its television was, and an ephemeral port picked afresh on every launch
   * would strand it: the address it stored would answer for nothing. Worse, a phone that can only
   * be told an address — because it is the hotspot the television is on and nothing is announced
   * over one of those — has no way to learn a random port at all.
   */
  private fun openServerSocket(): ServerSocket? {
    // The fixed port first, so a phone that has only been told an address can still get in. The
    // port used last time is the next best thing, and any port at all rather than no remote.
    val preferred = (listOf(LINK_DEFAULT_PORT) + listOfNotNull(store.lastPort())).distinct()
    preferred.forEach { port ->
      // Reusing the address matters on a relaunch: the socket the last run held lingers for a
      // minute or so afterwards, and without this the fixed port is exactly the one not available
      // when the app is restarted.
      runCatching {
        return ServerSocket().apply {
          reuseAddress = true
          bind(InetSocketAddress(port))
        }
      }
    }
    return runCatching { ServerSocket(0) }.getOrNull()
  }

  fun stop() {
    job?.cancel()
    job = null
    runCatching { serverSocket?.close() }
    serverSocket = null
    withdraw()
    _address.value = null
    synchronized(clients) {
      clients.forEach(LinkConnection::close)
      clients.clear()
    }
    _pairingCode.value = null
  }

  /** The viewer turning a phone away, and every phone it let in with it. */
  fun forgetPairedPhones() {
    store.forgetPairedPhones()
    synchronized(clients) {
      clients.forEach(LinkConnection::close)
      clients.clear()
    }
  }

  private suspend fun serve(socket: Socket) {
    val connection =
      runCatching {
          socket.soTimeout = HANDSHAKE_TIMEOUT_MS
          LinkConnection(socket)
        }
        .getOrNull() ?: return
    try {
      if (!authenticate(connection)) return
      // Past the handshake the phone may sit quiet for as long as it likes; it is a remote control,
      // not a heartbeat.
      socket.soTimeout = 0
      synchronized(clients) { clients.add(connection) }
      connection.send(RemoteControl.options())
      connection.send(RemoteControl.state(context))
      while (true) {
        val line = connection.readLine() ?: break
        val command = decodeCommand(line) ?: continue
        runCatching { RemoteControl.execute(context, command) }
          .onFailure { Log.w(LOG_TAG, "Command failed", it) }
        // A pick may change what else is on offer, so the list goes back with the new state.
        connection.send(RemoteControl.options())
        connection.send(RemoteControl.state(context))
      }
    } catch (error: Throwable) {
      Log.w(LOG_TAG, "A phone dropped out", error)
    } finally {
      synchronized(clients) { clients.remove(connection) }
      connection.close()
    }
  }

  /**
   * Lets a phone in, or does not.
   *
   * A phone that has been paired before proves it with the token it was given. A new one is shown a
   * code on the television and gets one attempt at it: a second guess would make six digits worth
   * guessing, and the viewer who is standing there can simply ask for a new code.
   */
  private suspend fun authenticate(connection: LinkConnection): Boolean {
    val line = connection.readLine() ?: return false
    return when (val command = decodeCommand(line)) {
      is LinkCommand.Hello -> {
        val known = store.isPaired(command.token)
        if (!known) {
          beginPairing()
          connection.send(LinkEvent.PairingRequired)
        }
        known
      }
      is LinkCommand.Pair -> {
        val expected = pendingCode
        val accepted =
          expected != null &&
            System.currentTimeMillis() < pendingCodeExpiresAt &&
            constantTimeEquals(expected, command.code)
        endPairing()
        if (accepted) {
          val token = newPairingToken()
          store.addPairedToken(token)
          connection.send(LinkEvent.Paired(token))
          Log.i(LOG_TAG, "Paired with ${command.name}")
        } else {
          connection.send(LinkEvent.Refused("That code did not match."))
        }
        accepted
      }
      else -> {
        beginPairing()
        connection.send(LinkEvent.PairingRequired)
        false
      }
    }
  }

  private fun beginPairing() {
    val code = newPairingCode()
    pendingCode = code
    pendingCodeExpiresAt = System.currentTimeMillis() + PAIRING_WINDOW_MS
    _pairingCode.value = code
    scope.launch {
      delay(PAIRING_WINDOW_MS)
      if (pendingCode == code) endPairing()
    }
  }

  private fun endPairing() {
    pendingCode = null
    pendingCodeExpiresAt = 0L
    _pairingCode.value = null
  }

  /**
   * Keeps the phone's progress bar honest while something plays.
   *
   * Only while it is actually moving: a paused television has nothing new to say, and the phone
   * already heard about the pause when it caused it.
   */
  private suspend fun broadcastState() {
    var lastSent: LinkEvent.State? = null
    var lastOptions: LinkEvent.Options? = null
    while (scope.isActive) {
      delay(STATE_INTERVAL_MS)
      val listeners = synchronized(clients) { clients.toList() }
      if (listeners.isEmpty()) continue
      // Tracks appear a moment after a stream opens, so the list is watched rather than sent once.
      val options = runCatching { RemoteControl.options() }.getOrNull()
      if (options != null && options != lastOptions) {
        lastOptions = options
        listeners.forEach { it.send(options) }
      }
      val state = runCatching { RemoteControl.state(context) }.getOrNull() ?: continue
      if (!state.playing && state == lastSent) continue
      lastSent = state
      listeners.forEach { it.send(state) }
    }
  }

  private fun advertise(port: Int) {
    val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
    val info =
      NsdServiceInfo().apply {
        serviceName = "GIZTV on ${Build.MODEL}"
        serviceType = LINK_SERVICE_TYPE
        setPort(port)
      }
    val listener =
      object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) = Unit

        override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
          Log.w(LOG_TAG, "The television could not announce itself: $errorCode")
        }

        override fun onServiceUnregistered(info: NsdServiceInfo) = Unit

        override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
      }
    registration = listener
    runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
  }

  private fun withdraw() {
    val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
    registration?.let { runCatching { manager.unregisterService(it) } }
    registration = null
  }
}

/** One phone's socket, and the only place either side's bytes are read or written. */
internal class LinkConnection(private val socket: Socket) {
  private val reader: BufferedReader = socket.getInputStream().bufferedReader()
  private val writer: BufferedWriter = socket.getOutputStream().bufferedWriter()

  fun readLine(): String? = runCatching { reader.readLine() }.getOrNull()

  @Synchronized
  fun send(event: LinkEvent) {
    runCatching {
      writer.write(event.encode())
      writer.newLine()
      writer.flush()
    }
  }

  @Synchronized
  fun send(command: LinkCommand) {
    runCatching {
      writer.write(command.encode())
      writer.newLine()
      writer.flush()
    }
  }

  fun close() {
    runCatching { socket.close() }
  }
}
