package org.ascensionassociation.fltr.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address

/** Tells "you're on mobile data" apart from "the box isn't answering". */
class HomeNetwork(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    /** True when the active network is Wi-Fi or Ethernet, the only ways to reach FLTR. */
    fun onLocalNetwork(): Boolean {
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /** The phone's IPv4 subnet on the active network, where an FLTR box would be, or null. */
    fun subnet(): Subnet? {
        val cm = connectivity ?: return null
        val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
        val own = props.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        val gateways = props.routes.filter { it.isDefaultRoute }.mapNotNull { it.gateway as? Inet4Address }
        return Subnet(own.address as Inet4Address, own.prefixLength, gateways)
    }
}

/** An IPv4 address with its prefix length, and the network's default gateways. */
data class Subnet(val address: Inet4Address, val prefixLength: Int, val gateways: List<Inet4Address>)
