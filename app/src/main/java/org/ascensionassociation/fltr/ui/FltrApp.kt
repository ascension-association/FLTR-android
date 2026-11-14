package org.ascensionassociation.fltr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.ascensionassociation.fltr.data.DeviceBody
import org.ascensionassociation.fltr.ui.activity.ActivityScreen
import org.ascensionassociation.fltr.ui.box.BoxScreen
import org.ascensionassociation.fltr.ui.components.ProblemState
import org.ascensionassociation.fltr.ui.devices.DevicesScreen
import org.ascensionassociation.fltr.ui.family.FamilyScreen
import org.ascensionassociation.fltr.ui.pair.PairScreen
import org.ascensionassociation.fltr.ui.sheets.AddPersonSheet
import org.ascensionassociation.fltr.ui.user.UserScreen

private enum class Tab(val route: String, val label: String, val title: String, val icon: ImageVector) {
    Family("family", "Family", "Family", Icons.Rounded.FamilyRestroom),
    Devices("devices", "Devices", "Devices", Icons.Rounded.Devices),
    Activity("activity", "Activity", "Activity", Icons.Rounded.History),
    Box("box", "FLTR", "FLTR box", Icons.Rounded.Router),
}

@Composable
fun FltrApp(vm: FltrViewModel) {
    val pairing by vm.pairing.collectAsStateWithLifecycle()
    val pairState by vm.pairState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    val current = pairing
    // Not paired yet, or a pairing link arrived while paired: show pairing.
    if (current == null || pairState != PairState.Idle) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            PairScreen(
                state = pairState,
                onCode = vm::pair,
                onFind = vm::findBox,
                onClaim = vm::claimBox,
                onConfirm = vm::confirmPairing,
                onSaveAnyway = vm::savePairingAnyway,
                onDismiss = vm::dismissPairing,
            )
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    } else {
        Home(vm, current, snackbar)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Home(vm: FltrViewModel, pairing: org.ascensionassociation.fltr.data.Pairing, snackbar: SnackbarHostState) {
    val state by vm.home.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tab = Tab.entries.firstOrNull { it.route == route }
    var addPerson by rememberSaveable { mutableStateOf(false) }
    var addPersonDevice by remember { mutableStateOf<DeviceBody?>(null) }

    // Poll while the app is on screen; stop in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, pairing) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.pollWhileVisible() }
    }

    val refreshLists: () -> Unit = {
        vm.refreshNow()
        when (tab) {
            Tab.Devices -> vm.refreshDevices()
            Tab.Activity -> vm.refreshBlocks()
            else -> Unit
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = if (tab != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (tab != null) {
                LargeTopAppBar(
                    title = { Text(tab.title) },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            }
        },
        bottomBar = {
            if (tab != null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = t == tab,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.Family && state.users.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { vm.refreshDevices(); addPerson = true },
                    icon = { Icon(Icons.Rounded.PersonAdd, contentDescription = null) },
                    text = { Text("Add person") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        // Nothing loaded yet: show progress, or what's wrong.
        if (!state.loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val problem = state.problem
                if (problem == null) {
                    CircularProgressIndicator()
                } else {
                    ProblemState(problem, onRetry = vm::refreshNow, onPairAgain = vm::forgetBox)
                }
            }
            return@Scaffold
        }

        NavHost(nav, startDestination = Tab.Family.route) {
            composable(Tab.Family.route) {
                FamilyScreen(
                    state = state,
                    contentPadding = padding,
                    onRefresh = refreshLists,
                    onPairAgain = vm::forgetBox,
                    onOpenUser = { nav.navigate("user/$it") },
                    onAddPerson = { vm.refreshDevices(); addPerson = true },
                    onPause = vm::setPaused,
                    onExtra = vm::addExtra,
                )
            }
            composable(Tab.Devices.route) {
                LaunchedEffect(Unit) { vm.refreshDevices() }
                DevicesScreen(
                    state = state,
                    contentPadding = padding,
                    refreshing = state.refreshing,
                    onRefresh = refreshLists,
                    onPairAgain = vm::forgetBox,
                    onAssign = vm::assignDevice,
                    onAddPerson = { device -> addPersonDevice = device; addPerson = true },
                )
            }
            composable(Tab.Activity.route) {
                LaunchedEffect(Unit) { vm.refreshBlocks() }
                ActivityScreen(
                    state = state,
                    contentPadding = padding,
                    refreshing = state.refreshing,
                    onRefresh = refreshLists,
                    onPairAgain = vm::forgetBox,
                )
            }
            composable(Tab.Box.route) {
                BoxScreen(
                    state = state,
                    pairing = pairing,
                    contentPadding = padding,
                    onRefresh = vm::refreshNow,
                    onPairAgain = vm::forgetBox,
                    onForget = vm::forgetBox,
                )
            }
            composable(
                "user/{name}",
                arguments = listOf(navArgument("name") { type = NavType.StringType }),
            ) { backStackEntry ->
                val name = backStackEntry.arguments?.getString("name").orEmpty()
                LaunchedEffect(name) {
                    vm.refreshBlocks()
                    vm.refreshDevices()
                }
                UserScreen(
                    name = name,
                    state = state,
                    onBack = { nav.popBackStack() },
                    onPause = vm::setPaused,
                    onExtra = vm::addExtra,
                    onSetAllowance = vm::setAllowance,
                    onAddDevices = vm::addDevices,
                    onRemoveDevice = vm::removeDevice,
                    onDelete = { user -> vm.deleteUser(user) { nav.popBackStack() } },
                    onLoadDevices = vm::refreshDevices,
                )
            }
        }

        if (addPerson) {
            AddPersonSheet(
                existingNames = state.users.map { it.name.lowercase() }.toSet(),
                seen = state.devices,
                initialDevices = listOfNotNull(addPersonDevice),
                onCreate = { name, allowance, devices ->
                    vm.createUser(name, allowance, devices) {
                        addPerson = false
                        addPersonDevice = null
                    }
                },
                onDismiss = { addPerson = false; addPersonDevice = null },
            )
        }
    }
}
