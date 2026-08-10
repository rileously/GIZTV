package com.giztv.tv.link

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LOG_TAG = "GizTvLink"
private const val CONNECT_TIMEOUT_MS = 6_000

/**
 * How long a host that answered the door has to say something.
 *
 * Opening a socket proves very little: a router, a proxy or a phone's own NAT will accept a
 * connection on any port and then sit there. Without this the remote waits on one of those for
 * ever, which is exactly what a sweep across a whole subnet will eventually find.
 */
private const val HANDSHAKE_TIMEOUT_MS = 8_000

/** Long enough to be past whatever interrupted it, short enough that nobody reaches for a button. */
private const val RECONNECT_DELAY_MS = 3_000L

/** Where the phone thinks it stands with the television. */
internal sealed interface LinkStatus {
  data object Idle : LinkStatus

  data object Searching : LinkStatus

  data class Connecting(val target: LinkTarget) : LinkStatus

  /** The television is showing a code and is waiting to be told it. */
  data class AwaitingCode(val target: LinkTarget) : LinkStatus

  data class Connected(val target: LinkTarget, val state: LinkEvent.State) : LinkStatus

  data class Failed(val reason: String) : LinkStatus
}

/**
 * The phone's end: find the television, get let in, then hold the socket open.
 *
 * Everything the remote shows comes back down the same connection it sends on, so a phone that has
 * been out of the room knows what it missed the moment it reconnects rather than having to ask.
 */
internal class LinkClient(private val context: Context) {
  private val store = LinkStore(context)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var connection: LinkConnection? = null
  private var job: Job? = null

  /** The search in progress, kept so it can be stopped before another is started on top. */
  private var discovery: NsdManager.DiscoveryListener? = null

  /** Held until the television has let this phone in, then sent. */
  @Volatile private var pending: LinkCommand? = null

  /**
   * Which attempt is the live one.
   *
   * A connection replaced by a newer attempt must not announce its own death: the screen would be
   * told the television had disconnected at the exact moment it was being connected to.
   */
  @Volatile private var generation = 0

  /** Set while the viewer is the one hanging up, so a deliberate close is not treated as a drop. */
  @Volatile private var hangingUp = false

  private val _status = MutableStateFlow<LinkStatus>(LinkStatus.Idle)
  val status: StateFlow<LinkStatus> = _status.asStateFlow()

  private val _options = MutableStateFlow(LinkEvent.Options(emptyList(), 0L))

  /** The player's settings, held apart from the state because they arrive on their own schedule. */
  val options: StateFlow<LinkEvent.Options> = _options.asStateFlow()

  private val _found = MutableStateFlow<List<LinkTarget>>(emptyList())

  /** Televisions seen on this network, for when there is more than one to choose between. */
  val found: StateFlow<List<LinkTarget>> = _found.asStateFlow()

  fun pairedTelevision(): LinkTarget? = store.lastTelevision()

  /**
   * Looks for televisions, by announcement and by sweep at the same time.
   *
   * The announcement is the quick way and works on an ordinary network. The sweep is the way that
   * works on a phone's own hotspot, where the television is a client of this very device and no
   * multicast passes between them, so nothing is ever announced to hear.
   */
  fun discover() {
    _status.value = LinkStatus.Searching
    _found.value = emptyList()
    sweep()
    val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    if (manager == null) {
      _status.value = LinkStatus.Failed("This phone cannot look for televisions.")
      return
    }
    // Android allows only a handful of searches at once and counts every one that was started and
    // never stopped. Reconnecting falls back to searching, so leaving them running meant a phone
    // that lost its television a few times could never look for it again.
    stopDiscovery(manager)
    val listener =
      object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit

        override fun onServiceFound(info: NsdServiceInfo) {
          runCatching { manager.resolveService(info, resolveListener(manager)) }
        }

        override fun onServiceLost(info: NsdServiceInfo) {
          _found.value = _found.value.filterNot { it.name == info.serviceName }
        }

        override fun onDiscoveryStopped(serviceType: String) = Unit

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
          _status.value = LinkStatus.Failed("The search could not start.")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
      }
    discovery = listener
    runCatching {
      manager.discoverServices(LINK_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }
      .onFailure { discovery = null }
  }

  private fun stopDiscovery(manager: NsdManager) {
    discovery?.let { runCatching { manager.stopServiceDiscovery(it) } }
    discovery = null
  }

  /** Knocks on every address on this network, and adds whatever answers to the list. */
  private fun sweep() {
    scope.launch {
      val ports =
        (listOf(LINK_DEFAULT_PORT) + listOfNotNull(store.lastTelevision()?.port)).distinct()
      val found = runCatching { scanForTelevisions(ports) }.getOrDefault(emptyList())
      // Anything the announcement turned up in the meantime keeps its proper name.
      val announced = _found.value
      _found.value = announced + found.filterNot { swept -> announced.any { it.host == swept.host } }
      if (_found.value.isEmpty() && _status.value is LinkStatus.Searching) {
        _status.value = LinkStatus.Failed("No television found on this network.")
      }
    }
  }

  /** Connecting to an address typed in by hand, for a network that hides everything on it. */
  fun connectTo(host: String, port: Int = LINK_DEFAULT_PORT) {
    connect(LinkTarget(name = "GIZTV at $host", host = host, port = port))
  }

  private fun resolveListener(manager: NsdManager) =
    object : NsdManager.ResolveListener {
      override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit

      override fun onServiceResolved(info: NsdServiceInfo) {
        val host = info.host?.hostAddress ?: return
        val target = LinkTarget(name = info.serviceName, host = host, port = info.port)
        _found.value = (_found.value.filterNot { it.name == target.name } + target)
      }
    }

  /**
   * Connects only if this phone is not already talking to the television.
   *
   * Opening the remote should not disturb a connection that is already carrying something — the
   * handover makes one moments before the remote appears, and reconnecting on top of it killed the
   * very socket that had just been used.
   */
  fun ensureConnected() {
    when (_status.value) {
      is LinkStatus.Connected,
      is LinkStatus.Connecting,
      is LinkStatus.AwaitingCode -> return
      else -> Unit
    }
    val target = store.lastTelevision()
    if (target == null) discover() else connect(target)
  }

  /** Connects, pairing first if this phone has not been let in before. */
  fun connect(target: LinkTarget, code: String? = null) {
    // Found what it was looking for; the search can stop.
    (context.getSystemService(Context.NSD_SERVICE) as? NsdManager)?.let(::stopDiscovery)
    val attempt = ++generation
    job?.cancel()
    job =
      scope.launch {
        _status.value = LinkStatus.Connecting(target)
        val socket =
          runCatching {
              Socket().apply {
                connect(InetSocketAddress(target.host, target.port), CONNECT_TIMEOUT_MS)
              }
            }
            .getOrElse {
              // The address it was last seen at is a hint, not a fact. If nothing answers there,
              // go and look for it rather than leaving the viewer at a dead end.
              _status.value = LinkStatus.Failed("That television did not answer. Looking again…")
              discover()
              return@launch
            }
        socket.soTimeout = HANDSHAKE_TIMEOUT_MS
        val link = LinkConnection(socket)
        connection = link
        val phoneName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val token = store.ownToken()
        if (code != null) {
          link.send(LinkCommand.Pair(code = code, name = phoneName))
        } else {
          link.send(LinkCommand.Hello(token = token.orEmpty().ifEmpty { "none" }, name = phoneName))
        }
        listen(link, socket, target, attempt)
      }
  }

  private suspend fun listen(
    link: LinkConnection,
    socket: Socket,
    target: LinkTarget,
    attempt: Int,
  ) {
    withContext(Dispatchers.IO) {
      var heardAnything = false
      while (true) {
        val line = link.readLine() ?: break
        // It has spoken GIZTV, so it is not a router holding the line open. From here it may take
        // as long as it likes between messages.
        if (!heardAnything) {
          heardAnything = true
          runCatching { socket.soTimeout = 0 }
        }
        when (val event = decodeEvent(line)) {
          LinkEvent.PairingRequired -> _status.value = LinkStatus.AwaitingCode(target)
          is LinkEvent.Paired -> {
            store.rememberTelevision(event.token, target.host, target.port, target.name)
            _status.value = LinkStatus.Connecting(target)
          }
          is LinkEvent.Refused -> {
            _status.value = LinkStatus.Failed(event.reason)
            break
          }
          is LinkEvent.State -> {
            _status.value = LinkStatus.Connected(target, event)
            // The first state is the television saying the phone is in. Anything queued while it
            // was still connecting goes now.
            pending?.let { queued ->
              pending = null
              link.send(queued)
            }
          }
          is LinkEvent.Options -> _options.value = event
          null -> Unit
        }
      }
      link.close()
      // Superseded by a newer attempt, or hung up on purpose. Either way this one has nothing to
      // report: saying anything here would overwrite the state of the connection that replaced it.
      if (attempt != generation || hangingUp) return@withContext
      when {
        // A television that goes quiet mid-evening is almost always a phone that changed network or
        // an app that was reopened, and the viewer should not have to go and press anything.
        // Still waiting for a code to be typed: say so and stay put. Reconnecting here is what
        // made the television issue a fresh number every few seconds.
        _status.value is LinkStatus.AwaitingCode -> Unit
        heardAnything -> {
          _status.value = LinkStatus.Failed("Reconnecting to ${target.name}…")
          delay(RECONNECT_DELAY_MS)
          if (attempt == generation && !hangingUp) connect(target)
        }
        // Answered the door and said nothing: not a television, whatever it is.
        else -> _status.value = LinkStatus.Failed("Nothing at ${target.host} answered as a television.")
      }
      Log.i(LOG_TAG, "Remote connection closed")
    }
  }

  /**
   * Answers the television's request for a code.
   *
   * Down the connection that asked, where one is still open. Opening a second would leave the
   * television waiting on the first and asking the viewer for another number.
   */
  fun submitCode(target: LinkTarget, code: String) {
    val phoneName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    val open = connection
    if (open != null && _status.value is LinkStatus.AwaitingCode) {
      scope.launch { open.send(LinkCommand.Pair(code = code, name = phoneName)) }
      return
    }
    connect(target, code)
  }

  fun send(command: LinkCommand) {
    val link = connection ?: return
    scope.launch { link.send(command) }
  }

  /**
   * Sends a title to the television, connecting first if this phone is not already talking to it.
   *
   * Returns whether there was a television to send it to at all: without one there is nothing to
   * offer and the caller should not pretend otherwise.
   */
  fun playOnTelevision(command: LinkCommand.Play): Boolean {
    if (_status.value is LinkStatus.Connected) {
      send(command)
      return true
    }
    val target = store.lastTelevision() ?: return false
    pending = command
    connect(target)
    return true
  }

  fun disconnect() {
    hangingUp = true
    generation++
    job?.cancel()
    job = null
    connection?.close()
    connection = null
    _status.value = LinkStatus.Idle
    hangingUp = false
  }

  /** Forgetting the television, so the next connection has to be approved on screen again. */
  fun forget() {
    disconnect()
    store.forgetTelevision()
  }
}
