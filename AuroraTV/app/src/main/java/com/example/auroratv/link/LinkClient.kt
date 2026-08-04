package com.example.auroratv.link

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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LOG_TAG = "GizTvLink"
private const val CONNECT_TIMEOUT_MS = 6_000

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

  private val _status = MutableStateFlow<LinkStatus>(LinkStatus.Idle)
  val status: StateFlow<LinkStatus> = _status.asStateFlow()

  private val _found = MutableStateFlow<List<LinkTarget>>(emptyList())

  /** Televisions seen on this network, for when there is more than one to choose between. */
  val found: StateFlow<List<LinkTarget>> = _found.asStateFlow()

  fun pairedTelevision(): LinkTarget? = store.lastTelevision()

  /**
   * Looks for televisions, starting with the one this phone already knows.
   *
   * The stored address is tried straight away because it is usually still right and saves the wait,
   * while the search runs anyway in case the router has moved it since.
   */
  fun discover() {
    _status.value = LinkStatus.Searching
    _found.value = emptyList()
    val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    if (manager == null) {
      _status.value = LinkStatus.Failed("This phone cannot look for televisions.")
      return
    }
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
    runCatching {
      manager.discoverServices(LINK_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }
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

  /** Connects, pairing first if this phone has not been let in before. */
  fun connect(target: LinkTarget, code: String? = null) {
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
              _status.value = LinkStatus.Failed("That television did not answer.")
              return@launch
            }
        val link = LinkConnection(socket)
        connection = link
        val phoneName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val token = store.ownToken()
        if (code != null) {
          link.send(LinkCommand.Pair(code = code, name = phoneName))
        } else {
          link.send(LinkCommand.Hello(token = token.orEmpty().ifEmpty { "none" }, name = phoneName))
        }
        listen(link, target)
      }
  }

  private suspend fun listen(link: LinkConnection, target: LinkTarget) {
    withContext(Dispatchers.IO) {
      while (true) {
        val line = link.readLine() ?: break
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
          is LinkEvent.State -> _status.value = LinkStatus.Connected(target, event)
          null -> Unit
        }
      }
      link.close()
      if (_status.value is LinkStatus.Connected) {
        _status.value = LinkStatus.Failed("The television disconnected.")
      }
      Log.i(LOG_TAG, "Remote connection closed")
    }
  }

  fun send(command: LinkCommand) {
    val link = connection ?: return
    scope.launch { link.send(command) }
  }

  fun disconnect() {
    job?.cancel()
    job = null
    connection?.close()
    connection = null
    _status.value = LinkStatus.Idle
  }

  /** Forgetting the television, so the next connection has to be approved on screen again. */
  fun forget() {
    disconnect()
    store.forgetTelevision()
  }
}
