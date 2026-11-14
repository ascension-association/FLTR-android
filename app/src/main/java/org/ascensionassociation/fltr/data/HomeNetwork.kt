package org.ascensionassociation.fltr.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Tells "you're on mobile data" apart from "the box isn't answering". */
class HomeNetwork(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    /** True when the active network is Wi-Fi or Ethernet, the only ways to reach FLTR. */
    fun onLocalNetwork(): Boolean {
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
