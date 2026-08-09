package android.net.nsd

import java.net.InetAddress

open class NsdServiceInfo {
    var serviceName: String = ""
    var serviceType: String = ""
    var host: InetAddress? = null
    var port: Int = 0
}

open class NsdManager {
    interface RegistrationListener {
        fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
        fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
    }

    interface DiscoveryListener {
        fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
        fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        fun onDiscoveryStarted(serviceType: String) {}
        fun onDiscoveryStopped(serviceType: String) {}
        fun onServiceFound(serviceInfo: NsdServiceInfo) {}
        fun onServiceLost(serviceInfo: NsdServiceInfo) {}
    }

    interface ResolveListener {
        fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        fun onServiceResolved(serviceInfo: NsdServiceInfo) {}
    }

    open fun registerService(serviceInfo: NsdServiceInfo, protocolType: Int, listener: RegistrationListener) {}
    open fun unregisterService(listener: RegistrationListener) {}
    open fun discoverServices(serviceType: String, protocolType: Int, listener: DiscoveryListener) {}
    open fun stopServiceDiscovery(listener: DiscoveryListener) {}
    open fun resolveService(serviceInfo: NsdServiceInfo, listener: ResolveListener) {}

    companion object {
        const val PROTOCOL_DNS_SD = 1
    }
}
