package net.koalastuff.koalashelf.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.koalastuff.koalashelf.core.data.i18n.AppLocale
import net.koalastuff.koalashelf.core.data.i18n.currentKoalaStrings
import net.koalastuff.koalashelf.core.data.prefs.PrefsRepository
import net.koalastuff.koalashelf.core.data.session.SavedAccount
import net.koalastuff.koalashelf.core.data.session.ServerTooOldException
import net.koalastuff.koalashelf.core.data.session.SessionRepository
import net.koalastuff.koalashelf.core.designsystem.KoalaRadius
import net.koalastuff.koalashelf.core.designsystem.KoalaSpacing
import net.koalastuff.koalashelf.core.designsystem.KoalaTheme
import net.koalastuff.koalashelf.core.designsystem.KoalaThemeMode
import net.koalastuff.koalashelf.core.designsystem.VinylDisc
import net.koalastuff.koalashelf.core.network.AbsError
import net.koalastuff.koalashelf.core.network.MIN_SERVER_VERSION
import net.koalastuff.koalashelf.core.network.isLoopbackHost
import net.koalastuff.koalashelf.core.network.isVersionAtLeast
import net.koalastuff.koalashelf.core.network.normalizeBaseUrl
import net.koalastuff.koalashelf.core.network.normalizeServerInput
import net.koalastuff.koalashelf.core.network.serverLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignInScreen(
    sessionRepository: SessionRepository,
    prefsRepository: PrefsRepository? = null,
    isAddingAccount: Boolean = false,
    prefillServer: String = "",
    prefillUsername: String = "",
    onSignedIn: () -> Unit,
    onCancelAdd: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = KoalaTheme.colors
    val typography = KoalaTheme.typography
    val strings = currentKoalaStrings
    val scope = rememberCoroutineScope()

    val accounts by sessionRepository.accounts.collectAsStateWithLifecycle()
    val recentServers by sessionRepository.recentServers.collectAsStateWithLifecycle()
    val expired by sessionRepository.expired.collectAsStateWithLifecycle()

    var serverUrl by rememberSaveable(prefillServer) {
        mutableStateOf(prefillServer.ifBlank { recentServers.firstOrNull() ?: "" })
    }
    var username by rememberSaveable(prefillUsername) { mutableStateOf(prefillUsername) }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    var checkingServer by remember { mutableStateOf(false) }
    var serverVersionFound by remember { mutableStateOf<String?>(null) }
    var serverProbeError by remember { mutableStateOf<String?>(null) }

    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    // Debounced live server probe (/status) - 2000ms debounce matching web
    LaunchedEffect(serverUrl) {
        serverVersionFound = null
        serverProbeError = null
        val trimmed = serverUrl.trim()
        if (trimmed.isEmpty()) {
            checkingServer = false
            return@LaunchedEffect
        }
        if (trimmed.startsWith("http://", ignoreCase = true) && !isLoopbackHost(trimmed)) {
            serverProbeError = strings.errInsecureHttp
            checkingServer = false
            return@LaunchedEffect
        }
        val cleaned = normalizeServerInput(serverUrl)
        if (cleaned.length < 8) {
            checkingServer = false
            return@LaunchedEffect
        }
        checkingServer = true
        delay(2000L)
        runCatching {
            sessionRepository.probeServer(cleaned)
        }.onSuccess { status ->
            checkingServer = false
            if (!isVersionAtLeast(status.serverVersion, MIN_SERVER_VERSION)) {
                serverProbeError = strings.errTooOld(status.serverVersion, MIN_SERVER_VERSION)
            } else {
                serverVersionFound = status.serverVersion
            }
        }.onFailure {
            checkingServer = false
        }
    }

    fun performSignIn() {
        val trimmed = serverUrl.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) && !isLoopbackHost(trimmed)) {
            submitError = strings.errInsecureHttp
            return
        }
        val cleanUrl = normalizeServerInput(serverUrl)
        if (cleanUrl.isBlank() || username.isBlank() || password.isBlank() || submitting) return
        submitting = true
        submitError = null
        scope.launch {
            try {
                sessionRepository.signIn(cleanUrl, username.trim(), password)
                submitting = false
                onSignedIn()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ServerTooOldException) {
                submitting = false
                submitError = strings.errTooOld(e.version, e.minVersion)
            } catch (e: AbsError) {
                submitting = false
                submitError = when (e.status) {
                    401 -> strings.errWrongCredentials
                    0 -> strings.errNotAbs
                    else -> e.message
                }
            } catch (_: Exception) {
                submitting = false
                submitError = strings.errUnreachable
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(KoalaSpacing.s24),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(KoalaRadius.cardLg),
            color = colors.card,
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .border(1.dp, colors.line, RoundedCornerShape(KoalaRadius.cardLg)),
        ) {
            Column(
                modifier = Modifier.padding(KoalaSpacing.s24),
                verticalArrangement = Arrangement.spacedBy(KoalaSpacing.s16),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Hero KoalaShelf Mascot (Koala holding vinyl record)
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(
                        id = net.koalastuff.koalashelf.core.designsystem.R.drawable.koalashelf_mascot,
                    ),
                    contentDescription = strings.appName,
                    modifier = Modifier.size(136.dp),
                )

                Text(
                    text = if (isAddingAccount) strings.addAccountTitle else strings.appName,
                    style = typography.size34,
                    color = colors.ink,
                )

                Text(
                    text = if (isAddingAccount) strings.addAccountIntro else strings.signInIntro,
                    style = typography.size14,
                    color = colors.muted,
                )

                if (expired) {
                    Surface(
                        shape = RoundedCornerShape(KoalaRadius.cover),
                        color = colors.accent.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = strings.sessionExpired,
                            style = typography.size13.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.accent,
                            modifier = Modifier.padding(KoalaSpacing.s12),
                        )
                    }
                }

                // Saved accounts quick switch (if any)
                if (accounts.isNotEmpty() && !isAddingAccount) {
                    Column(verticalArrangement = Arrangement.spacedBy(KoalaSpacing.s8)) {
                        Text(
                            text = strings.savedAccountsLabel.uppercase(),
                            style = typography.label,
                            color = colors.muted,
                        )
                        accounts.forEach { acc ->
                            Surface(
                                shape = RoundedCornerShape(KoalaRadius.card),
                                color = colors.panel,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (acc.tokens != null) {
                                            scope.launch {
                                                if (sessionRepository.switchTo(acc.key)) {
                                                    onSignedIn()
                                                }
                                            }
                                        } else {
                                            serverUrl = acc.baseUrl
                                            username = acc.user.username
                                        }
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = KoalaSpacing.s16, vertical = KoalaSpacing.s12),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(KoalaSpacing.s12),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccountCircle,
                                            contentDescription = null,
                                            tint = colors.accent,
                                        )
                                        Column {
                                            Text(
                                                text = acc.user.username,
                                                style = typography.size15.copy(fontWeight = FontWeight.SemiBold),
                                                color = colors.ink,
                                            )
                                            Text(
                                                text = acc.label,
                                                style = typography.size12,
                                                color = colors.muted,
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch { sessionRepository.forget(acc.key) }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = null,
                                            tint = colors.muted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Server URL Input
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text(strings.serverLabel) },
                    placeholder = { Text(strings.serverPlaceholder) },
                    leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Next,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.line,
                        focusedLabelColor = colors.accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                AnimatedVisibility(visible = checkingServer || serverVersionFound != null || serverProbeError != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(KoalaSpacing.s8),
                    ) {
                        when {
                            checkingServer -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = colors.muted,
                                )
                                Text(text = strings.serverChecking, style = typography.size12, color = colors.muted)
                            }
                            serverVersionFound != null -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = strings.serverFound(serverVersionFound!!),
                                    style = typography.size12.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.accent,
                                )
                            }
                            serverProbeError != null -> {
                                Text(
                                    text = serverProbeError!!,
                                    style = typography.size12,
                                    color = colors.accent,
                                )
                            }
                        }
                    }
                }

                // Recent servers chips
                if (recentServers.size > 1) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(KoalaSpacing.s8),
                        verticalArrangement = Arrangement.spacedBy(KoalaSpacing.s8),
                    ) {
                        recentServers.forEach { srv ->
                            Surface(
                                shape = RoundedCornerShape(KoalaRadius.pill),
                                color = colors.panel,
                                modifier = Modifier.clickable { serverUrl = srv },
                            ) {
                                Text(
                                    text = serverLabel(srv),
                                    style = typography.size12,
                                    color = colors.ink,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }

                // Username Input
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(strings.usernameLabel) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Next,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.line,
                        focusedLabelColor = colors.accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Password Input
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(strings.passwordLabel) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { performSignIn() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.line,
                        focusedLabelColor = colors.accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (submitError != null) {
                    Text(
                        text = submitError!!,
                        style = typography.size13.copy(fontWeight = FontWeight.Medium),
                        color = colors.accent,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KoalaSpacing.s12),
                ) {
                    if (isAddingAccount && onCancelAdd != null) {
                        OutlinedButton(
                            onClick = onCancelAdd,
                            shape = RoundedCornerShape(KoalaRadius.pill),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .weight(1f),
                        ) {
                            Text(text = strings.playerClose)
                        }
                    }

                    Button(
                        onClick = { performSignIn() },
                        enabled = !submitting && serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                        shape = RoundedCornerShape(KoalaRadius.pill),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent,
                        ),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .weight(1.4f),
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.onAccent,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = strings.connectingLabel)
                        } else {
                            Text(
                                text = if (isAddingAccount) strings.addAccountTitle else strings.signInSubmit,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Text(
                    text = strings.privacyNotice,
                    style = typography.size12,
                    color = colors.muted,
                )

                // Direct Locale & Theme Switcher on Sign-In screen
                if (prefsRepository != null) {
                    val currentPrefs by prefsRepository.prefs.collectAsStateWithLifecycle()
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Locale Pills (DE / EN)
                        Surface(
                            shape = RoundedCornerShape(KoalaRadius.pill),
                            color = colors.panel,
                        ) {
                            Row(modifier = Modifier.padding(2.dp)) {
                                listOf(AppLocale.DE to "DE", AppLocale.EN to "EN").forEach { (loc, label) ->
                                    val selected = (loc == AppLocale.DE && strings.isGerman) || (loc == AppLocale.EN && !strings.isGerman)
                                    Surface(
                                        shape = RoundedCornerShape(KoalaRadius.pill),
                                        color = if (selected) colors.sel else colors.panel,
                                        modifier = Modifier.clickable {
                                            prefsRepository.update { it.copy(locale = loc.tag) }
                                        },
                                    ) {
                                        Text(
                                            text = label,
                                            style = typography.size12.copy(fontWeight = FontWeight.Bold),
                                            color = if (selected) colors.selInk else colors.muted,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        )
                                    }
                                }
                            }
                        }

                        // Theme Mode Pills (System / Light / Dark)
                        Surface(
                            shape = RoundedCornerShape(KoalaRadius.pill),
                            color = colors.panel,
                        ) {
                            Row(modifier = Modifier.padding(2.dp)) {
                                listOf(
                                    KoalaThemeMode.SYSTEM.id to strings.settingsThemeSystem,
                                    KoalaThemeMode.LIGHT.id to strings.settingsThemeLight,
                                    KoalaThemeMode.DARK.id to strings.settingsThemeDark,
                                ).forEach { (modeId, label) ->
                                    val selected = currentPrefs.themeMode == modeId
                                    Surface(
                                        shape = RoundedCornerShape(KoalaRadius.pill),
                                        color = if (selected) colors.sel else colors.panel,
                                        modifier = Modifier.clickable {
                                            prefsRepository.update { it.copy(themeMode = modeId) }
                                        },
                                    ) {
                                        Text(
                                            text = label,
                                            style = typography.size12.copy(fontWeight = FontWeight.Bold),
                                            color = if (selected) colors.selInk else colors.muted,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitcherSheet(
    sessionRepository: SessionRepository,
    onDismiss: () -> Unit,
    onAddAccount: () -> Unit,
) {
    val colors = KoalaTheme.colors
    val typography = KoalaTheme.typography
    val strings = currentKoalaStrings
    val scope = rememberCoroutineScope()

    val activeKey by sessionRepository.activeKey.collectAsStateWithLifecycle()
    val groups = remember(sessionRepository.accounts.collectAsStateWithLifecycle().value, activeKey) {
        sessionRepository.accountsGroupedByServer()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        contentColor = colors.ink,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KoalaSpacing.s24, vertical = KoalaSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(KoalaSpacing.s16),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.navAccounts,
                    style = typography.size24,
                    color = colors.ink,
                )
                TextButton(onClick = onAddAccount) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = colors.accent)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = strings.addAccountTitle, color = colors.accent)
                }
            }

            groups.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(KoalaSpacing.s8)) {
                    Text(
                        text = group.label.uppercase(),
                        style = typography.label,
                        color = colors.muted,
                    )
                    group.accounts.forEach { acc ->
                        val isActive = acc.key == activeKey
                        Surface(
                            shape = RoundedCornerShape(KoalaRadius.card),
                            color = if (isActive) colors.sel else colors.panel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        if (sessionRepository.switchTo(acc.key)) {
                                            onDismiss()
                                        } else {
                                            onAddAccount()
                                        }
                                    }
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = KoalaSpacing.s16, vertical = KoalaSpacing.s12),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(KoalaSpacing.s12),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(colors.accent),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = acc.user.username.take(1).uppercase(),
                                            style = typography.size15.copy(fontWeight = FontWeight.Bold),
                                            color = colors.onAccent,
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = acc.user.username,
                                            style = typography.size15.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isActive) colors.selInk else colors.ink,
                                        )
                                        Text(
                                            text = group.label,
                                            style = typography.size12,
                                            color = colors.muted,
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isActive) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = colors.accent,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                sessionRepository.forget(acc.key)
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = strings.navSignOut,
                                            tint = colors.muted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(KoalaSpacing.s16))
        }
    }
}
