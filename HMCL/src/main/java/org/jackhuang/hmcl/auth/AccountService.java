// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth;

import com.google.gson.JsonObject;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.auth.authlibinjector.*;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftSession;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftService;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.offline.Skin;
import org.jackhuang.hmcl.auth.offline.YggdrasilServer;
import org.jackhuang.hmcl.auth.yggdrasil.*;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.util.function.ExceptionalSupplier;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jackhuang.hmcl.util.javafx.ObservableOptionalCache;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Authenticates accounts, prepares launch resources, and uploads skins.
///
/// Network operations block the calling thread. Stored-credential login serializes session changes
/// on the account monitor; interactive reauthentication returns detached state for later acceptance.
/// Returned authentication information belongs to the caller and must be closed after use.
/// A successful session refresh is retained even if subsequent injector preparation fails.
@NotNullByDefault
public final class AccountService {
    /// Supplies injector artifacts for external authentication and offline skins.
    private final AuthlibInjectorArtifactProvider artifactProvider;

    /// The launcher-configured Microsoft protocol service shared by all Microsoft accounts.
    private final MicrosoftService microsoftService;

    /// Protocol services and profile caches shared by accounts with the same external server URL.
    private final Map<String, YggdrasilService> yggdrasilServices = new ConcurrentHashMap<>();

    /// Creates an account service using the launcher's injector provider and Microsoft client configuration.
    public AccountService(AuthlibInjectorArtifactProvider artifactProvider, MicrosoftService microsoftService) {
        this.artifactProvider = Objects.requireNonNull(artifactProvider);
        this.microsoftService = Objects.requireNonNull(microsoftService);
    }

    /// Creates an offline identity, deriving its UUID from the name when no override is supplied.
    public OfflineAccount createOfflineAccount(String name, @Nullable UUID uuid, @Nullable Skin skin) {
        return new OfflineAccount(AccountID.generate(), name,
                uuid != null ? uuid : OfflineAccount.getUUIDFromUserName(name), skin);
    }

    /// Authenticates a new Microsoft account using an operation-specific OAuth callback.
    ///
    /// @throws AuthenticationException if authentication fails or returns an incomplete profile
    public MicrosoftAccount createMicrosoftAccount(OAuth.GrantFlow flow, OAuth.Callback callback) throws AuthenticationException {
        MicrosoftSession session = microsoftService.authenticate(flow, callback);
        if (session.profile() == null) session = microsoftService.refresh(session);
        if (!session.hasProfileName()) throw new ServerResponseMalformedException("Profile name is missing");
        return new MicrosoftAccount(AccountID.generate(), session, true);
    }

    /// Authenticates the existing Microsoft profile and returns detached credentials without changing the account.
    /// The caller commits the returned session only after accepting the result.
    ///
    /// @throws OAuthAccount.WrongAccountException if a different Minecraft profile is selected
    /// @throws AuthenticationException if authentication fails
    public MicrosoftAccount reauthenticateMicrosoftAccount(MicrosoftAccount account, OAuth.GrantFlow flow,
                                                            OAuth.Callback callback) throws AuthenticationException {
        MicrosoftAccount authenticated = createMicrosoftAccount(flow, callback);
        if (!account.getProfileID().equals(authenticated.getProfileID())) {
            throw new OAuthAccount.WrongAccountException(account.getProfileID(), authenticated.getProfileID());
        }
        MicrosoftAccount result = new MicrosoftAccount(account.getAccountID(), authenticated.getSession(), true);
        result.setPortable(account.isPortable());
        return result;
    }

    /// Authenticates a new external account, asking the selector to choose a profile when necessary.
    ///
    /// @throws NoCharacterException if the server offers no characters
    /// @throws AuthenticationException if authentication or profile selection fails
    public AuthlibInjectorAccount createAuthlibInjectorAccount(AuthlibInjectorServer server, String username,
                                                               String password, CharacterSelector selector) throws AuthenticationException {
        YggdrasilService service = getYggdrasilService(server);
        YggdrasilSession session = service.authenticate(username, password, UUIDs.toCompactString(UUID.randomUUID()));
        if (session.getSelectedProfile() == null) {
            if (session.getAvailableProfiles() == null || session.getAvailableProfiles().isEmpty()) throw new NoCharacterException();
            GameProfile selected = selector.select(service, session.getAvailableProfiles());
            session = service.refresh(session.getAccessToken(), session.getClientToken(), selected);
        }
        if (!session.hasProfileName()) throw new ServerResponseMalformedException("Profile name is missing");
        return new AuthlibInjectorAccount(AccountID.generate(), server, username, session);
    }

    /// Authenticates the existing external profile without changing its stored credentials.
    ///
    /// @throws CharacterDeletedException if the original profile is no longer available
    /// @throws AuthenticationException if authentication fails
    public AuthlibInjectorAccount reauthenticateAuthlibInjectorAccount(AuthlibInjectorAccount account, String password)
            throws AuthenticationException {
        AuthlibInjectorAccount authenticated;
        try {
            authenticated = createAuthlibInjectorAccount(account.getServer(), account.getLoginName(), password,
                    (service, profiles) -> profiles.stream().filter(profile -> profile.getId().equals(account.getProfileID()))
                            .findFirst().orElseThrow(NoSelectedCharacterException::new));
        } catch (NoSelectedCharacterException | NoCharacterException e) {
            throw new CharacterDeletedException();
        }
        if (!account.getProfileID().equals(authenticated.getProfileID())) throw new CharacterDeletedException();
        AuthlibInjectorAccount result = new AuthlibInjectorAccount(account.getAccountID(), account.getServer(),
                account.getLoginName(), authenticated.getSession());
        result.setPortable(account.isPortable());
        return result;
    }

    /// Restores a persisted profile into the launcher's cache and marks it stale for the next online query.
    /// No authentication or profile fetch is performed by this method.
    /// Accounts without a profile repository are ignored.
    ///
    /// @param account the account whose cached profile is being restored
    /// @param profile the persisted profile, whose ID must match the account's profile ID
    public void restoreProfile(Account account, CompleteGameProfile profile) {
        @Nullable var repository = getProfileRepository(account);
        if (repository != null) {
            repository.put(account.getProfileID(), profile);
            repository.invalidate(account.getProfileID());
        }
    }

    /// Commits detached credentials to the existing account without changing its identity or storage settings.
    ///
    /// @throws IllegalArgumentException if the candidate has a different account ID, type, or external server
    /// @throws AuthenticationException if its Minecraft profile differs or is incomplete
    public void acceptReauthentication(Account existing, Account candidate) throws AuthenticationException {
        if (!existing.getAccountID().equals(candidate.getAccountID())) throw new IllegalArgumentException("Account ID changed");
        if (existing instanceof MicrosoftAccount microsoft && candidate instanceof MicrosoftAccount refreshed) {
            microsoft.updateSession(refreshed.getSession());
        } else if (existing instanceof AuthlibInjectorAccount injector && candidate instanceof AuthlibInjectorAccount refreshed
                && injector.getServer().equals(refreshed.getServer()) && injector.getLoginName().equals(refreshed.getLoginName())) {
            injector.updateSession(refreshed.getSession());
        } else {
            throw new IllegalArgumentException("Account type or authentication server changed");
        }
    }

    /// Returns the shared protocol service for the server URL, creating it without network access if absent.
    public YggdrasilService getYggdrasilService(AuthlibInjectorServer server) {
        return yggdrasilServices.computeIfAbsent(server.getUrl(), url -> new YggdrasilService(new AuthlibInjectorProvider(url)));
    }

    /// Returns the profile repository for a supported online account, or `null` for other account types.
    private @Nullable ObservableOptionalCache<UUID, CompleteGameProfile, AuthenticationException> getProfileRepository(Account account) {
        if (account instanceof MicrosoftAccount) {
            return microsoftService.getProfileRepository();
        } else if (account instanceof AuthlibInjectorAccount injector) {
            return getYggdrasilService(injector.getServer()).getProfileRepository();
        }
        return null;
    }

    /// Returns a binding to the account's cached profile, or an empty optional for unsupported account types.
    ///
    /// When `quiet` is `false`, reading an absent or invalidated profile starts an asynchronous fetch.
    /// When `quiet` is `true`, the binding only observes cached values and never initiates a fetch.
    /// Failed fetches retain the cached profile, if any.
    public ObjectBinding<Optional<CompleteGameProfile>> getProfile(Account account, boolean quiet) {
        @Nullable var repository = getProfileRepository(account);
        return repository == null ? Bindings.createObjectBinding(Optional::empty) : repository.binding(account.getProfileID(), quiet);
    }

    /// Returns a texture binding that asynchronously fetches missing or invalidated profiles when read.
    /// An absent profile or malformed texture payload produces an empty optional.
    public ObjectBinding<Optional<Map<TextureType, Texture>>> getTextures(Account account) {
        return BindingMapping.of(getProfile(account, false)).map(profile -> profile.flatMap(it -> {
            try {
                return YggdrasilService.getTextures(it);
            } catch (ServerResponseMalformedException e) {
                LOG.warning("Failed to parse texture payload", e);
                return Optional.empty();
            }
        }));
    }

    /// Marks profile and external server metadata caches stale without discarding their cached values.
    /// Microsoft accounts also require session validation on their next login.
    /// This method does not initiate a network request itself; observing bindings may request a refresh.
    public void clearCache(Account account) {
        if (account instanceof MicrosoftAccount microsoft) {
            microsoft.invalidateAuthentication();
        }
        @Nullable var repository = getProfileRepository(account);
        if (repository != null) {
            repository.invalidate(account.getProfileID());
        }
        if (account instanceof AuthlibInjectorAccount injector) {
            injector.getServer().invalidateMetadataCache();
        }
    }

    /// Writes account credentials and the currently cached profile properties to caller-owned private storage.
    /// This method does not fetch profiles or refresh credentials and does not retain the target object.
    public void writePrivateData(Account account, JsonObject privateData) {
        account.writePrivateData(privateData);
        @Nullable var repository = getProfileRepository(account);
        if (repository != null) {
            repository.getImmediately(account.getProfileID()).ifPresent(profile ->
                    privateData.add("profileProperties", JsonUtils.GSON.toJsonTree(profile.getProperties())));
        }
    }

    /// Returns whether the launcher implements skin uploads for this account type.
    /// This does not check server permissions or credential validity.
    public boolean canUploadSkin(Account account) {
        Objects.requireNonNull(account);
        return account instanceof MicrosoftAccount || account instanceof AuthlibInjectorAccount;
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
            microsoftService.uploadSkin(microsoft.getSession().accessToken(), isSlim, file);
        } else if (account instanceof AuthlibInjectorAccount yggdrasil) {
            getYggdrasilService(yggdrasil.getServer()).uploadSkin(yggdrasil.getProfileID(),
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

    /// Returns offline launch information with a fresh random token and no skin injection.
    public AuthInfo logInWithoutSkin(OfflineAccount account) {
        return new AuthInfo(account.getProfileName(), account.getProfileID(), UUIDs.toCompactString(UUID.randomUUID()),
                AuthInfo.USER_TYPE_MSA, "{}");
    }

    /// Validates or refreshes a Microsoft session while the account monitor is held.
    private AuthInfo logInMicrosoft(MicrosoftAccount account) throws AuthenticationException {
        MicrosoftSession session = account.getSession();
        var service = microsoftService;
        if (!account.isAuthenticated() || !session.hasProfileName() || System.currentTimeMillis() > session.notAfter()) {
            if (!session.hasProfileName() || !service.validate(session.notAfter(), session.tokenType(), session.accessToken())) {
                session = service.refresh(session);
            }
            account.updateSession(session);
        }
        return session.toAuthInfo();
    }

    /// Validates or refreshes a Yggdrasil session while the account monitor is held.
    private AuthInfo logInYggdrasil(AuthlibInjectorAccount account) throws AuthenticationException {
        YggdrasilSession session = account.getSession();
        var service = getYggdrasilService(account.getServer());
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
