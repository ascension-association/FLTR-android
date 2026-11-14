package org.ascensionassociation.fltr

import android.app.Application
import org.ascensionassociation.fltr.data.CredentialStore
import org.ascensionassociation.fltr.data.HomeNetwork

class FltrApplication : Application() {
    val credentials by lazy { CredentialStore(this) }
    val homeNetwork by lazy { HomeNetwork(this) }
}
