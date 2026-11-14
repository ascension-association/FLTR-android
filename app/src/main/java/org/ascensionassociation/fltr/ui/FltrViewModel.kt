package org.ascensionassociation.fltr.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ascensionassociation.fltr.FltrApplication
import org.ascensionassociation.fltr.data.ApiException
import org.ascensionassociation.fltr.data.BlockEntry
import org.ascensionassociation.fltr.data.CredentialStore
import org.ascensionassociation.fltr.data.DeviceBody
import org.ascensionassociation.fltr.data.FltrApi
import org.ascensionassociation.fltr.data.FltrStatus
import org.ascensionassociation.fltr.data.FltrUser
import org.ascensionassociation.fltr.data.HomeNetwork
import org.ascensionassociation.fltr.data.InvalidPairingException
import org.ascensionassociation.fltr.data.Pairing
import org.ascensionassociation.fltr.data.PutUserBody
import org.ascensionassociation.fltr.data.SeenDevice
import org.ascensionassociation.fltr.util.displayName
import org.ascensionassociation.fltr.util.formatDuration

/** Something that stops the app from talking to FLTR, shown in place of (or above) the data. */
sealed interface Problem {
    /** Unreachable or 403. [onLocalNetwork] is false on mobile data. */
    data class OffHomeNetwork(val onLocalNetwork: Boolean) : Problem
    data object Unpaired : Problem
    data object WrongBox : Problem
    data class RateLimited(val seconds: Long?) : Problem
    data class Other(val message: String) : Problem
}

data class HomeState(
    val status: FltrStatus? = null,
    val users: List<FltrUser> = emptyList(),
    val devices: List<SeenDevice> = emptyList(),
    val blocks: List<BlockEntry> = emptyList(),
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val problem: Problem? = null,
    val lastUpdated: Instant? = null,
) {
    fun user(name: String): FltrUser? = users.firstOrNull { it.name == name }
}

sealed interface PairState {
    data object Idle : PairState
    /** A pairing link arrived from outside the app; ask before trusting it. */
    data class Confirm(val pairing: Pairing) : PairState
    data object Checking : PairState
    data class Failed(val message: String, val pairing: Pairing?, val canSaveAnyway: Boolean) : PairState
}

class FltrViewModel(
    private val credentials: CredentialStore,
    private val homeNetwork: HomeNetwork,
) : ViewModel() {

    private val _pairing = MutableStateFlow(credentials.load())
    val pairing: StateFlow<Pairing?> = _pairing.asStateFlow()

    private val _pairState = MutableStateFlow<PairState>(PairState.Idle)
    val pairState: StateFlow<PairState> = _pairState.asStateFlow()

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    /** One-off messages for the snackbar. */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var api: FltrApi? = _pairing.value?.let(::FltrApi)
    private var pairJob: Job? = null

    // ---- Pairing -------------------------------------------------------

    /** From the scanner or the paste box: the person chose this, so check it straight away. */
    fun pair(raw: String) = parseThen(raw) { verifyAndSave(it) }

    /** From a gkfltr:// link opened elsewhere: confirm first, a web page could have sent it. */
    fun pairFromLink(raw: String) = parseThen(raw) { _pairState.value = PairState.Confirm(it) }

    fun confirmPairing(pairing: Pairing) = verifyAndSave(pairing)

    fun savePairingAnyway(pairing: Pairing) = savePairing(pairing)

    fun dismissPairing() {
        pairJob?.cancel()
        _pairState.value = PairState.Idle
    }

    fun forgetBox() {
        credentials.clear()
        api = null
        _pairing.value = null
        _home.value = HomeState()
        _pairState.value = PairState.Idle
    }

    private inline fun parseThen(raw: String, next: (Pairing) -> Unit) {
        try {
            next(Pairing.parse(raw))
        } catch (e: InvalidPairingException) {
            _pairState.value = PairState.Failed(e.message ?: "That code didn't work.", null, false)
        }
    }

    private fun verifyAndSave(pairing: Pairing) {
        pairJob?.cancel()
        _pairState.value = PairState.Checking
        pairJob = viewModelScope.launch {
            try {
                FltrApi(pairing).status()
                savePairing(pairing)
            } catch (e: ApiException) {
                val offline = e is ApiException.Unreachable || e is ApiException.NotHome
                val message = when {
                    offline && !homeNetwork.onLocalNetwork() ->
                        "Connect this phone to your home Wi-Fi, then try again."
                    offline -> "Couldn't reach FLTR at ${pairing.displayHost}. Is this phone on your home Wi-Fi?"
                    e is ApiException.Unauthorized ->
                        "That code has been replaced. Show a fresh one with “ramekin restart fltr”."
                    else -> e.message ?: "Pairing failed."
                }
                _pairState.value = PairState.Failed(message, pairing, canSaveAnyway = offline)
            }
        }
    }

    private fun savePairing(pairing: Pairing) {
        credentials.save(pairing)
        api = FltrApi(pairing)
        _home.value = HomeState()
        _pairing.value = pairing
        _pairState.value = PairState.Idle
        viewModelScope.launch {
            _messages.send("Paired with FLTR at ${pairing.displayHost}")
        }
    }

    // ---- Loading -------------------------------------------------------

    /**
     * Refreshes status and users every 30 s while the UI is visible. FLTR
     * measures use once a minute, so polling faster shows nothing new.
     */
    suspend fun pollWhileVisible() {
        while (true) {
            refresh(showSpinner = false)
            delay(POLL_MILLIS)
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refresh(showSpinner = true) }
    }

    fun refreshDevices() = launchCall { api ->
        val devices = api.devices()
        _home.update { it.copy(devices = devices) }
    }

    fun refreshBlocks() = launchCall { api ->
        val blocks = api.blocks(limit = 500).asReversed()
        _home.update { it.copy(blocks = blocks) }
    }

    private suspend fun refresh(showSpinner: Boolean) {
        val api = api ?: return
        if (showSpinner) _home.update { it.copy(refreshing = true) }
        try {
            val status = api.status()
            val users = api.users()
            _home.update {
                it.copy(status = status, users = users, loaded = true, problem = null, lastUpdated = Instant.now())
            }
        } catch (e: ApiException) {
            _home.update { it.copy(problem = problemFor(e)) }
        } finally {
            if (showSpinner) _home.update { it.copy(refreshing = false) }
        }
    }

    // ---- Actions -------------------------------------------------------

    fun setPaused(user: FltrUser, paused: Boolean) = launchCall { api ->
        replaceUser(api.setPaused(user.name, paused))
        _messages.send(
            if (paused) "${displayName(user.name)} is paused until the daily reset"
            else "${displayName(user.name)} can continue"
        )
    }

    fun addExtra(user: FltrUser, seconds: Long) = launchCall { api ->
        replaceUser(api.addExtra(user.name, seconds))
        _messages.send(
            if (seconds > 0) "Gave ${displayName(user.name)} ${formatDuration(seconds)} more today"
            else "Took back ${formatDuration(-seconds)} from ${displayName(user.name)}"
        )
    }

    /** [seconds] null means "no override": the rules file and -allowance decide. */
    fun setAllowance(user: FltrUser, seconds: Long?) = launchCall { api ->
        val devices = user.settingsDevices.map { it.toBody() }
        if (seconds == null && devices.isEmpty()) {
            if (user.managed) api.deleteUser(user.name)
        } else {
            api.putUser(user.name, PutUserBody(allowanceSeconds = seconds, devices = devices))
        }
        reloadUsers(api)
        _messages.send("Saved ${displayName(user.name)}'s daily time")
    }

    fun createUser(name: String, allowanceSeconds: Long, devices: List<DeviceBody>, onDone: () -> Unit) =
        launchCall { api ->
            detachFromOtherUsers(api, name, devices)
            api.putUser(name, PutUserBody(allowanceSeconds = allowanceSeconds, devices = devices))
            reloadUsers(api)
            onDone()
            _messages.send("Added ${displayName(name)}")
        }

    /** One PUT for all of them: PUT replaces the device list, so separate calls would race. */
    fun addDevices(user: FltrUser, devices: List<DeviceBody>) = launchCall { api ->
        if (devices.isEmpty()) return@launchCall
        detachFromOtherUsers(api, user.name, devices)
        val current = user.settingsDevices.map { it.toBody() }
        val added = devices.filterNot { new -> current.any { it.matches(new) } }
        api.putUser(user.name, bodyWithDevices(user, current + added))
        reloadUsers(api)
        _messages.send(
            if (devices.size == 1) "Added the device to ${displayName(user.name)}"
            else "Added ${devices.size} devices to ${displayName(user.name)}"
        )
    }

    fun removeDevice(user: FltrUser, device: DeviceBody) = launchCall { api ->
        val remaining = user.settingsDevices.map { it.toBody() }.filterNot { it.matches(device) }
        val body = bodyWithDevices(user, remaining)
        if (body.allowanceSeconds == null && remaining.isEmpty()) api.deleteUser(user.name)
        else api.putUser(user.name, body)
        reloadUsers(api)
        _messages.send("Removed the device from ${displayName(user.name)}")
    }

    /** Assigns a device seen on the network to an existing user. */
    fun assignDevice(device: DeviceBody, userName: String) {
        val user = _home.value.user(userName)
        if (user != null) {
            addDevices(user, listOf(device))
        } else {
            launchCall { api ->
                detachFromOtherUsers(api, userName, listOf(device))
                api.putUser(userName, PutUserBody(devices = listOf(device)))
                reloadUsers(api)
                _messages.send("Added the device to ${displayName(userName)}")
            }
        }
    }

    fun deleteUser(user: FltrUser, onDone: () -> Unit) = launchCall { api ->
        api.deleteUser(user.name)
        reloadUsers(api)
        onDone()
        _messages.send(
            if (user.devices.any { !it.editable })
                "Removed the app's settings for ${displayName(user.name)}; rules.txt still lists them"
            else "Removed ${displayName(user.name)}"
        )
    }

    // ---- Helpers -------------------------------------------------------

    /**
     * PUT replaces all of a user's settings, so a device change has to resend
     * the allowance. The API doesn't say whether a managed user's allowance is
     * an override or comes from rules.txt, so for managed users the current
     * value is sent, which keeps it as it is today. For users only in
     * rules.txt nothing is sent, so their rules allowance keeps applying.
     */
    private fun bodyWithDevices(user: FltrUser, devices: List<DeviceBody>) = PutUserBody(
        allowanceSeconds = if (user.managed) user.allowanceSeconds else null,
        devices = devices,
    )

    /** A device should belong to one person: take it out of anyone else's app settings first. */
    private suspend fun detachFromOtherUsers(api: FltrApi, keep: String, devices: List<DeviceBody>) {
        for (other in _home.value.users) {
            if (other.name == keep) continue
            val theirs = other.settingsDevices.map { it.toBody() }
            val left = theirs.filterNot { mine -> devices.any { it.matches(mine) } }
            if (left.size == theirs.size) continue
            val body = bodyWithDevices(other, left)
            if (body.allowanceSeconds == null && left.isEmpty()) api.deleteUser(other.name)
            else api.putUser(other.name, body)
        }
    }

    /** After a change: reload people, and the seen devices too, since each shows its owner. */
    private suspend fun reloadUsers(api: FltrApi) {
        val users = api.users()
        _home.update { it.copy(users = users, lastUpdated = Instant.now()) }
        if (_home.value.devices.isNotEmpty()) {
            val devices = api.devices()
            _home.update { it.copy(devices = devices) }
        }
    }

    private fun replaceUser(user: FltrUser) {
        _home.update { state -> state.copy(users = state.users.map { if (it.name == user.name) user else it }) }
    }

    private fun launchCall(block: suspend (FltrApi) -> Unit): Job = viewModelScope.launch {
        val api = api ?: return@launch
        try {
            block(api)
        } catch (e: ApiException) {
            val problem = problemFor(e)
            if (problem is Problem.Unpaired || problem is Problem.WrongBox) {
                _home.update { it.copy(problem = problem) }
            }
            _messages.send(messageFor(e))
        }
    }

    private fun problemFor(e: ApiException): Problem = when (e) {
        is ApiException.Unreachable, is ApiException.NotHome -> Problem.OffHomeNetwork(homeNetwork.onLocalNetwork())
        is ApiException.Unauthorized -> Problem.Unpaired
        is ApiException.WrongBox -> Problem.WrongBox
        is ApiException.RateLimited -> Problem.RateLimited(e.retryAfterSeconds)
        else -> Problem.Other(e.message ?: "Something went wrong.")
    }

    private fun messageFor(e: ApiException): String = when (e) {
        is ApiException.Unreachable, is ApiException.NotHome ->
            if (homeNetwork.onLocalNetwork()) "Couldn't reach FLTR. Try again in a moment."
            else "Connect to your home Wi-Fi to make changes."
        else -> e.message ?: "Something went wrong."
    }

    companion object {
        private const val POLL_MILLIS = 30_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FltrApplication
                FltrViewModel(app.credentials, app.homeNetwork)
            }
        }
    }
}

/** Device values are normalized by FLTR (lowercase MAC, IP with prefix); compare loosely. */
fun DeviceBody.matches(other: DeviceBody): Boolean =
    type == other.type && normalized(value) == normalized(other.value)

private fun normalized(v: String) = v.lowercase().removeSuffix("/32").removeSuffix("/128")
