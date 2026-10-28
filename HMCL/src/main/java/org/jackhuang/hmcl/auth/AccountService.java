// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth;

import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.auth.authlibinjector.*;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftSession;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.offline.Skin;
import org.jackhuang.hmcl.auth.offline.YggdrasilServer;
import org.jackhuang.hmcl.auth.yggdrasil.*;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.util.function.ExceptionalSupplier;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static java.nio.charset.StandardCharsets.UTF_8;

/// Authenticates accounts, prepares launch resources, and uploads skins.
///
/// Login operations block the calling thread and serialize session changes on the account monitor.
/// Returned authentication information belongs to the caller and must be closed after use.
/// A successful session refresh is retained even if subsequent injector preparation fails.
@NotNullByDefault
public final class AccountService {
    /// Supplies injector artifacts for external authentication and offline skins.
    private final AuthlibInjectorArtifactProvider artifactProvider;

    /// Creates an account service using the launcher's injector artifact provider.
    public AccountService(AuthlibInjectorArtifactProvider artifactProvider) {
        this.artifactProvider = Objects.requireNonNull(artifactProvider);
    }

    /// Returns whether the launcher implements skin uploads for this account type.
    /// This does not check server permissions or credential validity.
    public boolean canUploadSkin(Account account) {
        Objects.requireNonNull(account);
        return account instanceof MicrosoftAccount || account instanceof YggdrasilAccount;
    }

    /// Uploads a skin using the account's current session without refreshing credentials or profile caches.
    /// This method reads the file and contacts the skin service on the calling thread.
    ///
    /// @param account the account whose skin is to be changed
    /// @param isSlim whether the skin uses the slim arm model
    /// @param file the skin image to upload
    /// @throws AuthenticationException if reading the image or uploading it fails
    /// @throws UnsupportedOperationException if the account type or its authentication provider does not support uploads
    public void uploadSkin(Account account, boolean isSlim, Path file) throws AuthenticationException {
        Objects.requireNonNull(account);
        Objects.requireNonNull(file);
        if (account instanceof MicrosoftAccount microsoft) {
            microsoft.getService().uploadSkin(microsoft.getSession().accessToken(), isSlim, file);
        } else if (account instanceof YggdrasilAccount yggdrasil) {
            yggdrasil.getYggdrasilService().uploadSkin(yggdrasil.getProfileID(),
                    yggdrasil.getSession().getAccessToken(), isSlim, file);
        } else {
            throw new UnsupportedOperationException("Skin uploads are unsupported for account type: " + account.getClass().getName());
        }
    }

    /// Logs in with stored credentials and prepares any required injector resources.
    ///
    /// This method does not request a password or start an interactive OAuth flow.
    ///
    /// @throws CredentialExpiredException if stored credentials cannot be refreshed
    /// @throws AuthenticationException if authentication or injector preparation fails;
    ///         interruption while awaiting resources restores the thread's interrupted status
    /// @throws IllegalArgumentException if the account type is unsupported
    public AuthInfo logIn(Account account) throws AuthenticationException {
        synchronized (account) {
            if (account instanceof OfflineAccount offline) {
                return logInOffline(offline);
            } else if (account instanceof MicrosoftAccount microsoft) {
                return logInMicrosoft(microsoft);
            } else if (account instanceof AuthlibInjectorAccount injector) {
                return inject(injector, () -> logInYggdrasil(injector));
            } else if (account instanceof YggdrasilAccount yggdrasil) {
                return logInYggdrasil(yggdrasil);
            }
            throw new IllegalArgumentException("Unsupported account type: " + account.getClass().getName());
        }
    }

    /// Returns launch information using cached credentials without contacting the authentication server.
    /// Offline accounts still prepare their configured skin and may need an injector artifact.
    ///
    /// @throws CredentialExpiredException if an online account has no cached profile name
    /// @throws NotLoggedInException if external authentication has no cached artifact or server metadata
    /// @throws AuthenticationException if offline skin preparation fails
    /// @throws IllegalArgumentException if the account type is unsupported
    public AuthInfo playOffline(Account account) throws AuthenticationException {
        synchronized (account) {
            if (account instanceof OfflineAccount offline) {
                return logInOffline(offline);
            } else if (account instanceof MicrosoftAccount microsoft) {
                MicrosoftSession session = microsoft.getSession();
                if (!session.hasProfileName()) {
                    throw new CredentialExpiredException("Profile name is missing");
                }
                return session.toAuthInfo();
            } else if (account instanceof YggdrasilAccount yggdrasil) {
                YggdrasilSession session = yggdrasil.getSession();
                if (!session.hasProfileName()) {
                    throw new CredentialExpiredException("Profile name is missing");
                }
                AuthInfo auth = session.toAuthInfo();
                if (account instanceof AuthlibInjectorAccount injector) {
                    var artifact = artifactProvider.getArtifactInfoImmediately();
                    var metadata = injector.getServer().getMetadataResponse();
                    if (artifact.isEmpty() || metadata.isEmpty()) {
                        throw new NotLoggedInException();
                    }
                    return new InjectorAuthInfo(auth, artifact.get(), injector.getServer().getUrl(), metadata.get());
                }
                return auth;
            }
            throw new IllegalArgumentException("Unsupported account type: " + account.getClass().getName());
        }
    }

    /// Authenticates a classic account with a password, retaining its selected profile.
    ///
    /// @throws CharacterDeletedException if the authenticated account no longer offers the profile
    /// @throws AuthenticationException if authentication, profile selection, or injector preparation fails
    /// @throws IllegalArgumentException if the account is not a Yggdrasil account
    public AuthInfo logInWithPassword(ClassicAccount account, String password) throws AuthenticationException {
        synchronized (account) {
            if (account instanceof AuthlibInjectorAccount injector) {
                return inject(injector, () -> authenticateYggdrasil(injector, password));
            } else if (account instanceof YggdrasilAccount yggdrasil) {
                return authenticateYggdrasil(yggdrasil, password);
            }
            throw new IllegalArgumentException("Unsupported account type: " + account.getClass().getName());
        }
    }

    /// Returns offline launch information with a fresh random token and no skin injection.
    public AuthInfo logInWithoutSkin(OfflineAccount account) {
        return new AuthInfo(account.getProfileName(), account.getProfileID(), UUIDs.toCompactString(UUID.randomUUID()),
                AuthInfo.USER_TYPE_MSA, "{}");
    }

    /// Validates or refreshes a Microsoft session while the account monitor is held.
    private AuthInfo logInMicrosoft(MicrosoftAccount account) throws AuthenticationException {
        MicrosoftSession session = account.getSession();
        var service = account.getService();
        if (!account.isAuthenticated() || !session.hasProfileName() || System.currentTimeMillis() > session.notAfter()) {
            if (!session.hasProfileName() || !service.validate(session.notAfter(), session.tokenType(), session.accessToken())) {
                session = service.refresh(session);
            }
            account.updateSession(session);
        }
        return session.toAuthInfo();
    }

    /// Validates or refreshes a Yggdrasil session while the account monitor is held.
    private AuthInfo logInYggdrasil(YggdrasilAccount account) throws AuthenticationException {
        YggdrasilSession session = account.getSession();
        var service = account.getYggdrasilService();
        if (!session.hasProfileName() || !service.validate(session.getAccessToken(), session.getClientToken())) {
            try {
                session = service.refresh(session.getAccessToken(), session.getClientToken(), null);
            } catch (RemoteAuthenticationException e) {
                if ("ForbiddenOperationException".equals(e.getRemoteName())) {
                    throw new CredentialExpiredException(e);
                }
                throw e;
            }
            account.updateSession(session);
        }
        return session.toAuthInfo();
    }

    /// Authenticates and reselects the existing Yggdrasil profile while the account monitor is held.
    private AuthInfo authenticateYggdrasil(YggdrasilAccount account, String password) throws AuthenticationException {
        var service = account.getYggdrasilService();
        YggdrasilSession session = service.authenticate(account.getLoginName(), password, UUIDs.toCompactString(UUID.randomUUID()));
        if (session.getSelectedProfile() == null) {
            if (session.getAvailableProfiles() == null || session.getAvailableProfiles().isEmpty()) {
                throw new CharacterDeletedException();
            }
            GameProfile selected = session.getAvailableProfiles().stream()
                    .filter(profile -> profile.getId().equals(account.getProfileID()))
                    .findFirst().orElseThrow(CharacterDeletedException::new);
            session = service.refresh(session.getAccessToken(), session.getClientToken(), selected);
        } else if (!session.getSelectedProfile().getId().equals(account.getProfileID())) {
            throw new CharacterDeletedException();
        }
        account.updateSession(session);
        return session.toAuthInfo();
    }

    /// Prepares offline skin injection only when a non-default skin is configured.
    private AuthInfo logInOffline(OfflineAccount account) throws AuthenticationException {
        AuthInfo auth = logInWithoutSkin(account);
        @Nullable Skin skin = account.getSkin();
        if (skin == null || skin.type() == Skin.Type.DEFAULT) {
            return auth;
        }
        return new OfflineAuthInfo(auth, await(prepareArtifact()), skin);
    }

    /// Fetches server metadata and the injector concurrently with authentication.
    private AuthInfo inject(AuthlibInjectorAccount account,
                            ExceptionalSupplier<AuthInfo, AuthenticationException> login) throws AuthenticationException {
        CompletableFuture<String> metadata = CompletableFuture.supplyAsync(() -> {
            try {
                return account.getServer().fetchMetadataResponse();
            } catch (IOException e) {
                throw new CompletionException(new ServerDisconnectException(e));
            }
        });
        CompletableFuture<AuthlibInjectorArtifactInfo> artifact = prepareArtifact();
        AuthInfo auth = login.get();
        String prefetched = await(metadata);
        return new InjectorAuthInfo(auth, await(artifact), account.getServer().getUrl(), prefetched);
    }

    /// Obtains an injector artifact asynchronously, preserving the download-specific failure type.
    private CompletableFuture<AuthlibInjectorArtifactInfo> prepareArtifact() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return artifactProvider.getArtifactInfo();
            } catch (IOException e) {
                throw new CompletionException(new AuthlibInjectorDownloadException(e));
            }
        });
    }

    /// Waits for a resource, preserving authentication failures and the interrupted status.
    private static <T> T await(CompletableFuture<T> future) throws AuthenticationException {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthenticationException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof AuthenticationException authenticationException) {
                throw authenticationException;
            }
            throw new AuthenticationException(e.getCause());
        }
    }

    /// Adds the injector agent and prefetched server metadata to launch arguments.
    @NotNullByDefault
    private static final class InjectorAuthInfo extends AuthInfo {
        /// The injector artifact used for this launch.
        private final AuthlibInjectorArtifactInfo artifact;
        /// The external authentication endpoint.
        private final String serverUrl;
        /// The metadata response to pass to the injector.
        private final String metadata;

        /// Copies credentials and captures the resources needed for injection.
        private InjectorAuthInfo(AuthInfo auth, AuthlibInjectorArtifactInfo artifact, String serverUrl, String metadata) {
            super(auth.getUsername(), auth.getUUID(), auth.getAccessToken(), auth.getUserType(), auth.getUserProperties());
            this.artifact = artifact;
            this.serverUrl = serverUrl;
            this.metadata = metadata;
        }

        @Override
        public Arguments getLaunchArguments(LaunchOptions options) {
            return new Arguments().addJVMArguments(
                    "-javaagent:" + artifact.location() + "=" + serverUrl,
                    "-Dauthlibinjector.side=client",
                    "-Dauthlibinjector.yggdrasil.prefetched=" + Base64.getEncoder().encodeToString(metadata.getBytes(UTF_8)));
        }
    }

    /// Owns the local skin server created when preparing a managed game launch.
    @NotNullByDefault
    private static final class OfflineAuthInfo extends AuthInfo {
        /// The injector artifact used for this launch.
        private final AuthlibInjectorArtifactInfo artifact;
        /// The skin configuration captured at login.
        private final Skin skin;
        /// The local skin server, created lazily for a managed launch.
        private @Nullable YggdrasilServer server;

        /// Copies the offline identity and captures its skin and injector artifact.
        private OfflineAuthInfo(AuthInfo auth, AuthlibInjectorArtifactInfo artifact, Skin skin) {
            super(auth.getUsername(), auth.getUUID(), auth.getAccessToken(), auth.getUserType(), auth.getUserProperties());
            this.artifact = artifact;
            this.skin = skin;
        }

        /// Starts the local skin server for a managed launch, or returns `null` for a detached launch.
        /// Repeated calls stop the previously started server before replacing it.
        ///
        /// @throws IOException if the server cannot start or skin loading fails with a non-I/O exception
        @Override
        public @Nullable Arguments getLaunchArguments(LaunchOptions options) throws IOException {
            if (!options.isDaemon()) return null;
            if (server != null) server.stop();
            server = new YggdrasilServer(0);
            server.start();
            try {
                server.addCharacter(new YggdrasilServer.Character(getUUID(), getUsername(), skin.load(getUsername()).run()));
            } catch (IOException e) {
                // A missing skin does not prevent launching with the offline identity.
            } catch (Exception e) {
                server.stop();
                server = null;
                throw new IOException(e);
            }
            return new Arguments().addJVMArguments(
                    "-javaagent:" + artifact.location() + "=http://localhost:" + server.getListeningPort(),
                    "-Dauthlibinjector.side=client");
        }

        /// Stops the local skin server, if present. Repeated calls have no effect.
        @Override
        public void close() {
            if (server != null) {
                server.stop();
                server = null;
            }
        }
    }
}
