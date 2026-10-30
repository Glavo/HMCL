// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

import com.google.gson.JsonObject;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.auth.authlibinjector.*;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftService;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftSession;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.offline.Skin;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.RemoteAuthenticationException;
import org.jackhuang.hmcl.auth.yggdrasil.Texture;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilSession;
import org.jackhuang.hmcl.game.OAuthServer;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.skin.InvalidSkinException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static javafx.collections.FXCollections.observableArrayList;
import static org.jackhuang.hmcl.setting.SettingsManager.*;
import static org.jackhuang.hmcl.ui.FXUtils.onInvalidating;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author huangyuhui
 */
public final class Accounts {
    private Accounts() {
    }

    private static final AuthlibInjectorArtifactProvider AUTHLIB_INJECTOR_DOWNLOADER = createAuthlibInjectorArtifactProvider();

    /// Handles interactive OAuth and supplies the launcher-configured Microsoft client ID.
    public static final OAuthServer.Factory OAUTH_CALLBACK = new OAuthServer.Factory();

    /// The Microsoft client configuration and profile cache shared by account creation and later operations.
    private static final MicrosoftService MICROSOFT_SERVICE = new MicrosoftService(OAUTH_CALLBACK);

    /// Performs account operations using HMCL's protocol services and injector configuration.
    private static final AccountService ACCOUNT_SERVICE = new AccountService(AUTHLIB_INJECTOR_DOWNLOADER, MICROSOFT_SERVICE);

    /// Logs in with stored credentials and prepares launch resources.
    ///
    /// @see AccountService#logIn(Account)
    public static AuthInfo logIn(Account account) throws AuthenticationException {
        return ACCOUNT_SERVICE.logIn(account);
    }

    /// Prepares a launch using cached credentials and available authentication resources.
    ///
    /// @see AccountService#playOffline(Account)
    public static AuthInfo playOffline(Account account) throws AuthenticationException {
        return ACCOUNT_SERVICE.playOffline(account);
    }

    /// Returns offline launch information without preparing skin injection.
    public static AuthInfo logInWithoutSkin(OfflineAccount account) {
        return ACCOUNT_SERVICE.logInWithoutSkin(account);
    }

    /// Returns whether the launcher implements skin uploads for this account type.
    ///
    /// @see AccountService#canUploadSkin(Account)
    public static boolean canUploadSkin(@NotNull Account account) {
        return ACCOUNT_SERVICE.canUploadSkin(account);
    }

    /// Uploads a skin using the account's current session without refreshing credentials or profile caches.
    ///
    /// @see AccountService#uploadSkin(Account, boolean, Path)
    public static void uploadSkin(@NotNull Account account, boolean isSlim, @NotNull Path file) throws AuthenticationException {
        ACCOUNT_SERVICE.uploadSkin(account, isSlim, file);
    }

    /// Returns an observable profile, fetching missing or stale data asynchronously when the binding is read.
    public static ObjectBinding<Optional<CompleteGameProfile>> getProfile(Account account) {
        return ACCOUNT_SERVICE.getProfile(account, false);
    }

    /// Returns an observable texture map backed by the launcher's profile cache.
    public static ObjectBinding<Optional<Map<TextureType, Texture>>> getTextures(Account account) {
        return ACCOUNT_SERVICE.getTextures(account);
    }

    /// Invalidates cached authentication and profile data without discarding stored credentials.
    public static void clearCache(Account account) {
        ACCOUNT_SERVICE.clearCache(account);
    }

    /// Creates an offline account with an optional UUID and skin override.
    public static OfflineAccount createOfflineAccount(String name, @Nullable UUID uuid, @Nullable Skin skin) {
        return ACCOUNT_SERVICE.createOfflineAccount(name, uuid, skin);
    }

    /// Authenticates a new Microsoft account using the supplied operation callback.
    public static MicrosoftAccount createMicrosoftAccount(OAuth.GrantFlow flow, OAuth.Callback callback) throws AuthenticationException {
        return ACCOUNT_SERVICE.createMicrosoftAccount(flow, callback);
    }

    /// Authenticates a new external account and selects a character when necessary.
    public static AuthlibInjectorAccount createAuthlibInjectorAccount(AuthlibInjectorServer server, String username,
                                                                     String password, CharacterSelector selector) throws AuthenticationException {
        return ACCOUNT_SERVICE.createAuthlibInjectorAccount(server, username, password, selector);
    }

    /// Obtains detached Microsoft credentials for the same profile without changing the existing account.
    public static MicrosoftAccount reauthenticateMicrosoftAccount(MicrosoftAccount account, OAuth.GrantFlow flow,
                                                                  OAuth.Callback callback) throws AuthenticationException {
        return ACCOUNT_SERVICE.reauthenticateMicrosoftAccount(account, flow, callback);
    }

    /// Obtains detached external credentials for the same profile without changing the existing account.
    public static AuthlibInjectorAccount reauthenticateAuthlibInjectorAccount(AuthlibInjectorAccount account, String password)
            throws AuthenticationException {
        return ACCOUNT_SERVICE.reauthenticateAuthlibInjectorAccount(account, password);
    }

    /// Commits accepted credentials to the existing account while preserving its identity and storage settings.
    public static void acceptReauthentication(Account existing, Account candidate) throws AuthenticationException {
        ACCOUNT_SERVICE.acceptReauthentication(existing, candidate);
    }

    private static final ObservableList<Account> accounts = observableArrayList(account -> new Observable[]{account});
    /// Keeps quiet profile bindings alive while their account objects are in the account list.
    /// Identity keys remain stable when an account's portability flag changes.
    private static final Map<Account, ObjectBinding<Optional<CompleteGameProfile>>> profileBindings = new IdentityHashMap<>();

    /// Tracks profile cache changes so refreshed private data is persisted independently of account notifications.
    private static void observeProfile(Account account) {
        if (profileBindings.containsKey(account)) {
            return;
        }
        ObjectBinding<Optional<CompleteGameProfile>> binding = ACCOUNT_SERVICE.getProfile(account, true);
        binding.addListener((observable, oldProfile, newProfile) -> updateAccountMetadataRecords());
        profileBindings.put(account, binding);
    }

    /// Detaches the repository binding when an account object leaves the account list.
    private static void stopObservingProfile(Account account) {
        @Nullable ObjectBinding<Optional<CompleteGameProfile>> binding = profileBindings.remove(account);
        if (binding != null) {
            binding.dispose();
        }
    }

    private static final ObjectProperty<Account> selectedAccount = new SimpleObjectProperty<>(Accounts.class, "selectedAccount");

    /**
     * True if {@link #init()} hasn't been called.
     */
    private static boolean initialized = false;

    /// Captures public metadata and currently cached private account data without fetching profiles.
    private static SerializedAccount serializeAccount(Account account) {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("type", AccountType.of(account).id());
        account.writeMetadata(metadata);
        JsonObject privateData = new JsonObject();
        ACCOUNT_SERVICE.writePrivateData(account, privateData);
        return new SerializedAccount(metadata, privateData);
    }

    /// Ensures account IDs are unique across local and shared account metadata records before accounts are instantiated.
    private static AccountIDNormalization ensureUniqueAccountIDs() {
        Set<String> usedAccountIDs = new HashSet<>();
        boolean localChanged = LegacyConfigMigrator.assignAccountIDs(
                SettingsManager.gameAccounts(),
                usedAccountIDs,
                false);
        boolean sharedChanged = LegacyConfigMigrator.assignAccountIDs(
                SettingsManager.userGameAccounts(),
                usedAccountIDs,
                true);
        return new AccountIDNormalization(localChanged, sharedChanged);
    }

    /// Result of normalizing account IDs across loaded metadata stores.
    ///
    /// @param localChanged whether the per-workspace account metadata changed
    /// @param sharedChanged whether the shared account metadata changed
    private record AccountIDNormalization(boolean localChanged, boolean sharedChanged) {
    }

    /// Returns account IDs from metadata records.
    ///
    /// @param first the first metadata record list
    /// @param second the second metadata record list
    /// @return account IDs found in the metadata records
    private static List<AccountID> getAccountIDs(List<JsonObject> first, List<JsonObject> second) {
        ArrayList<AccountID> accountIDs = new ArrayList<>(first.size() + second.size());
        addAccountIDs(accountIDs, first);
        addAccountIDs(accountIDs, second);
        return accountIDs;
    }

    /// Adds account IDs from metadata records to a target list.
    ///
    /// @param accountIDs the target account ID list
    /// @param metadataRecords the metadata records to read
    private static void addAccountIDs(List<AccountID> accountIDs, List<JsonObject> metadataRecords) {
        for (JsonObject metadata : metadataRecords) {
            @Nullable AccountID accountID = Account.getAccountID(metadata);
            if (accountID != null && !accountIDs.contains(accountID)) {
                accountIDs.add(accountID);
            }
        }
    }

    private static void updateAccountMetadataRecords() {
        // don't update the underlying account records before data loading is completed
        // otherwise it might cause data loss
        if (!initialized)
            return;
        ArrayList<JsonObject> globalMetadata = new ArrayList<>();
        LinkedHashMap<AccountID, JsonObject> globalPrivateData = new LinkedHashMap<>();
        ArrayList<JsonObject> portableMetadata = new ArrayList<>();
        LinkedHashMap<AccountID, JsonObject> portablePrivateData = new LinkedHashMap<>();

        for (Account account : accounts) {
            SerializedAccount serialized = serializeAccount(account);
            if (account.isPortable()) {
                portableMetadata.add(serialized.metadata());
                if (!serialized.privateData().isEmpty()) {
                    portablePrivateData.put(account.getAccountID(), serialized.privateData());
                }
            } else {
                globalMetadata.add(serialized.metadata());
                if (!serialized.privateData().isEmpty()) {
                    globalPrivateData.put(account.getAccountID(), serialized.privateData());
                }
            }
        }

        List<AccountID> retainedAccountIDs = getAccountIDs(globalMetadata, portableMetadata);
        if (!SettingsManager.isUserGameAccountsReadOnly())
            SettingsManager.updateUserGameAccounts(globalMetadata, globalPrivateData, retainedAccountIDs);
        if (!SettingsManager.isGameAccountsReadOnly())
            SettingsManager.updateGameAccounts(portableMetadata, portablePrivateData, retainedAccountIDs);
    }

    /// Returns whether the account metadata and credential files selected by the portability flag are read-only.
    ///
    /// @param portable whether the account is stored in the local account file
    public static boolean isAccountFilesReadOnly(boolean portable) {
        return portable ? SettingsManager.isGameAccountsReadOnly() : SettingsManager.isUserGameAccountsReadOnly();
    }

    /// Returns whether the files containing the given account are read-only.
    public static boolean isAccountFilesReadOnly(Account account) {
        return isAccountFilesReadOnly(account.isPortable());
    }

    /// Returns whether the given account may be removed from its current account files.
    public static boolean canRemoveAccount(Account account) {
        return !isAccountFilesReadOnly(account);
    }

    /// Returns whether the given account may be moved between local and user account files.
    public static boolean canMoveAccount(Account account) {
        return !SettingsManager.isGameAccountsReadOnly() && !SettingsManager.isUserGameAccountsReadOnly();
    }

    /// Backs up and overwrites the account metadata and credential files selected by the portability flag.
    ///
    /// @param portable whether the target account files are local
    /// @throws IOException if saving either file fails
    public static void forceOverwriteAccountFiles(boolean portable) throws IOException {
        if (portable) {
            SettingsManager.forceOverwriteGameAccounts();
        } else {
            SettingsManager.forceOverwriteUserGameAccounts();
        }
    }

    /// Backs up and overwrites the account files containing the given account.
    ///
    /// @throws IOException if saving either file fails
    public static void forceOverwriteAccountFiles(Account account) throws IOException {
        forceOverwriteAccountFiles(account.isPortable());
    }

    /// Backs up and overwrites both local and user account metadata and credential files.
    ///
    /// @throws IOException if saving either file fails
    public static void forceOverwriteAccountFiles() throws IOException {
        if (SettingsManager.isGameAccountsReadOnly()) {
            SettingsManager.forceOverwriteGameAccounts();
        }
        if (SettingsManager.isUserGameAccountsReadOnly()) {
            SettingsManager.forceOverwriteUserGameAccounts();
        }
    }

    /// Restores an account and its cached profile, returning `null` for invalid persisted data.
    private static @Nullable Account parseAccount(JsonObject record, boolean portable) {
        try {
            AccountID accountID = Account.readAccountID(record);
            JsonObject privateData = SettingsManager.getAccountPrivateData(accountID, portable);
            Account account = switch (AccountType.fromId(JsonUtils.getString(record, "type"))) {
                case OFFLINE -> {
                    String name = requireAccountString(record, "profileName");
                    @Nullable String uuid = JsonUtils.getString(record, "profileID");
                    UUID profileID = uuid == null ? OfflineAccount.getUUIDFromUserName(name) : UUIDs.parse(uuid);
                    @Nullable Skin skin = Skin.fromStorage(record.get("skin") instanceof JsonObject value ? value : null);
                    yield new OfflineAccount(accountID, name, profileID, skin);
                }
                case MICROSOFT -> new MicrosoftAccount(accountID, MicrosoftSession.fromStorage(record, privateData), false);
                case AUTHLIB_INJECTOR -> new AuthlibInjectorAccount(accountID,
                        getOrCreateAuthlibInjectorServer(requireAccountString(record, "serverBaseURL")),
                        requireAccountString(record, "loginName"), YggdrasilSession.fromStorage(record, privateData));
            };
            if (!(account instanceof OfflineAccount) && privateData.get("profileProperties") instanceof JsonObject propertiesObject) {
                Map<String, String> properties = JsonUtils.GSON.fromJson(propertiesObject, JsonUtils.mapTypeOf(String.class, String.class));
                ACCOUNT_SERVICE.restoreProfile(account, new CompleteGameProfile(account.getProfileID(), account.getProfileName(), properties));
            }
            return account;
        } catch (Exception e) {
            LOG.warning("Failed to load account: " + describeAccountRecord(record), e);
            return null;
        }
    }

    /// Reads a required primitive member of persisted account metadata as a string.
    ///
    /// @throws IllegalArgumentException if the member is missing, null, or cannot be read as a string
    private static String requireAccountString(JsonObject record, String name) {
        @Nullable String value = JsonUtils.getString(record, name);
        if (value == null) throw new IllegalArgumentException(name + " is missing");
        return value;
    }

    /// Serialized account metadata and private data.
    ///
    /// @param metadata public metadata stored in `accounts.json`
    /// @param privateData private account data stored in account private data
    private record SerializedAccount(JsonObject metadata, JsonObject privateData) {
    }

    /// Returns a safe account record description for diagnostics.
    private static String describeAccountRecord(JsonObject record) {
        AccountID accountID = Account.getAccountID(record);
        if (accountID != null) {
            return accountID.toString();
        }

        String type = JsonUtils.getString(record, "type");
        return type != null ? "{type=" + type + "}" : "<unknown>";
    }

    /// Called when it's ready to load accounts from [SettingsManager#settings()].
    public static void init() {
        if (initialized)
            throw new IllegalStateException("Already initialized");

        AccountIDNormalization accountIDNormalization = ensureUniqueAccountIDs();
        if (accountIDNormalization.localChanged()) {
            SettingsManager.saveGameAccountMetadataRecords();
        }
        if (accountIDNormalization.sharedChanged()) {
            SettingsManager.saveUserGameAccountMetadataRecords();
        }

        accounts.forEach(Accounts::observeProfile);
        accounts.addListener((ListChangeListener<Account>) change -> {
            while (change.next()) {
                change.getRemoved().forEach(Accounts::stopObservingProfile);
                change.getAddedSubList().forEach(Accounts::observeProfile);
            }
        });

        // load accounts
        Account selected = null;
        for (JsonObject record : getAccountMetadataRecords()) {
            Account account = parseAccount(record, true);
            if (account != null) {
                account.setPortable(true);
                accounts.add(account);
                if (JsonUtils.getBoolean(record, "selected", false)) {
                    selected = account;
                }
            }
        }

        for (JsonObject record : getUserAccountMetadataRecords()) {
            Account account = parseAccount(record, false);
            if (account != null) {
                accounts.add(account);
            }
        }

        AccountID selectedAccountID = settings().selectedAccountProperty().get();
        if (selected == null && selectedAccountID != null) {
            for (Account account : accounts) {
                if (account.getAccountID().equals(selectedAccountID)) {
                    selected = account;
                    break;
                }
            }
        }

        if (selected == null && !accounts.isEmpty()) {
            selected = accounts.get(0);
        }

        if (!SettingsManager.isUserSettingsReadOnly()
                && !SettingsManager.userSettings().enableOfflineAccountProperty().get())
            for (Account account : accounts) {
                if (account instanceof MicrosoftAccount) {
                    UserSettings userSettings = userSettings();
                    userSettings.enableOfflineAccountProperty().set(true);
                    break;
                }
            }

        if (!SettingsManager.isUserSettingsReadOnly()
                && !SettingsManager.userSettings().enableOfflineAccountProperty().get())
            accounts.addListener(new ListChangeListener<Account>() {
                @Override
                public void onChanged(Change<? extends Account> change) {
                    while (change.next()) {
                        for (Account account : change.getAddedSubList()) {
                            if (account instanceof MicrosoftAccount) {
                                accounts.removeListener(this);
                                UserSettings userSettings = userSettings();
                                userSettings.enableOfflineAccountProperty().set(true);
                                return;
                            }
                        }
                    }
                }
            });

        selectedAccount.set(selected);

        InvalidationListener listener = o -> {
            // this method first checks whether the current selection is valid
            // if it's valid, the underlying account records will be updated
            // otherwise, the first account will be selected as an alternative(or null if accounts is empty)
            Account account = selectedAccount.get();
            if (accounts.isEmpty()) {
                if (account == null) {
                    // valid
                } else {
                    // the previously selected account is gone, we can only set it to null here
                    selectedAccount.set(null);
                }
            } else {
                if (accounts.contains(account)) {
                    // valid
                } else {
                    // the previously selected account is gone
                    selectedAccount.set(accounts.get(0));
                }
            }
        };
        selectedAccount.addListener(listener);
        selectedAccount.addListener(onInvalidating(() -> {
            Account account = selectedAccount.get();
            if (account != null)
                settings().selectedAccountProperty().set(account.getAccountID());
            else
                settings().selectedAccountProperty().set(null);
        }));
        accounts.addListener(listener);
        accounts.addListener(onInvalidating(Accounts::updateAccountMetadataRecords));

        initialized = true;

        getAuthlibInjectorServers().addListener(onInvalidating(Accounts::removeDanglingAuthlibInjectorAccounts));

        if (selected != null) {
            Account finalSelected = selected;
            Schedulers.io().execute(() -> {
                try {
                    logIn(finalSelected);
                } catch (Throwable e) {
                    LOG.warning("Failed to log " + finalSelected + " in", e);
                }
            });
        }

        for (AuthlibInjectorServer server : getAuthlibInjectorServers()) {
            if (selected instanceof AuthlibInjectorAccount && ((AuthlibInjectorAccount) selected).getServer() == server)
                continue;
            Schedulers.io().execute(() -> {
                try {
                    server.fetchMetadataResponse();
                } catch (IOException e) {
                    LOG.warning("Failed to fetch authlib-injector server metadata: " + server, e);
                }
            });
        }
    }

    public static ObservableList<Account> getAccounts() {
        return accounts;
    }

    public static Account getSelectedAccount() {
        return selectedAccount.get();
    }

    public static void setSelectedAccount(Account selectedAccount) {
        Accounts.selectedAccount.set(selectedAccount);
    }

    public static ObjectProperty<Account> selectedAccountProperty() {
        return selectedAccount;
    }

    // ==== authlib-injector ====
    private static AuthlibInjectorArtifactProvider createAuthlibInjectorArtifactProvider() {
        String authlibinjectorLocation = System.getProperty("hmcl.authlibinjector.location");
        if (authlibinjectorLocation != null) {
            LOG.info("Using specified authlib-injector: " + authlibinjectorLocation);
            return new SimpleAuthlibInjectorArtifactProvider(Path.of(authlibinjectorLocation));
        }

        String authlibInjectorVersion = JarUtils.getAttribute("hmcl.authlib-injector.version", null);
        if (authlibInjectorVersion == null)
            throw new AssertionError("Missing hmcl.authlib-injector.version");

        String authlibInjectorFileName = "authlib-injector-" + authlibInjectorVersion + ".jar";
        return new AuthlibInjectorExtractor(Accounts.class.getResource("/assets/" + authlibInjectorFileName),
                Metadata.DEPENDENCIES_DIRECTORY.resolve("universal").resolve(authlibInjectorFileName));
    }

    private static AuthlibInjectorServer getOrCreateAuthlibInjectorServer(String url) {
        return getAuthlibInjectorServers().stream()
                .filter(server -> url.equals(server.getUrl()))
                .findFirst()
                .orElseGet(() -> {
                    AuthlibInjectorServer server = new AuthlibInjectorServer(url);
                    getAuthlibInjectorServers().add(server);
                    return server;
                });
    }

    /**
     * After an {@link AuthlibInjectorServer} is removed, the associated accounts should also be removed.
     * This method performs a check and removes the dangling accounts.
     */
    private static void removeDanglingAuthlibInjectorAccounts() {
        accounts.stream()
                .filter(AuthlibInjectorAccount.class::isInstance)
                .map(AuthlibInjectorAccount.class::cast)
                .filter(it -> !getAuthlibInjectorServers().contains(it.getServer()))
                .filter(Accounts::canRemoveAccount)
                .collect(toList())
                .forEach(accounts::remove);
    }
    // ====

    /// Returns the localized name for a supported account type.
    public static String getLocalizedLoginTypeName(AccountType type) {
        return i18n(switch (type) {
            case OFFLINE -> "account.methods.offline";
            case MICROSOFT -> "account.methods.microsoft";
            case AUTHLIB_INJECTOR -> "account.methods.authlib_injector";
        });
    }

    public static String localizeErrorMessage(Exception exception) {
        if (exception instanceof NoCharacterException) {
            return i18n("account.failed.no_character");
        } else if (exception instanceof ServerDisconnectException) {
            if (exception.getCause() instanceof SSLException) {
                if (exception.getCause().getMessage() != null && exception.getCause().getMessage().contains("Remote host terminated")) {
                    return i18n("account.failed.connect_authentication_server");
                }
                if (exception.getCause().getMessage() != null && (exception.getCause().getMessage().contains("No name matching") || exception.getCause().getMessage().contains("No subject alternative DNS name matching"))) {
                    return i18n("account.failed.dns");
                }
                return i18n("account.failed.ssl");
            } else {
                return i18n("account.failed.connect_authentication_server");
            }
        } else if (exception instanceof ServerResponseMalformedException) {
            return i18n("account.failed.server_response_malformed");
        } else if (exception instanceof RemoteAuthenticationException) {
            RemoteAuthenticationException remoteException = (RemoteAuthenticationException) exception;
            String remoteMessage = remoteException.getRemoteMessage();
            if ("ForbiddenOperationException".equals(remoteException.getRemoteName()) && remoteMessage != null) {
                if (remoteMessage.contains("Invalid credentials")) {
                    return i18n("account.failed.invalid_credentials");
                } else if (remoteMessage.contains("Invalid token")) {
                    return i18n("account.failed.invalid_token");
                } else if (remoteMessage.contains("Invalid username or password")) {
                    return i18n("account.failed.invalid_password");
                } else {
                    return remoteMessage;
                }
            } else if ("ResourceException".equals(remoteException.getRemoteName()) && remoteMessage != null) {
                if (remoteMessage.contains("The requested resource is no longer available")) {
                    return i18n("account.failed.migration");
                } else {
                    return remoteMessage;
                }
            }
            return exception.getMessage();
        } else if (exception instanceof AuthlibInjectorDownloadException) {
            return i18n("account.failed.injector_download_failure");
        } else if (exception instanceof CharacterDeletedException) {
            return i18n("account.failed.character_deleted");
        } else if (exception instanceof InvalidSkinException) {
            return i18n("account.skin.invalid_skin");
        } else if (exception instanceof MicrosoftService.XboxAuthorizationException) {
            long errorCode = ((MicrosoftService.XboxAuthorizationException) exception).getErrorCode();
            if (errorCode == MicrosoftService.XboxAuthorizationException.ADD_FAMILY) {
                return i18n("account.methods.microsoft.error.add_family");
            } else if (errorCode == MicrosoftService.XboxAuthorizationException.COUNTRY_UNAVAILABLE) {
                return i18n("account.methods.microsoft.error.country_unavailable");
            } else if (errorCode == MicrosoftService.XboxAuthorizationException.MISSING_XBOX_ACCOUNT) {
                return i18n("account.methods.microsoft.error.missing_xbox_account");
            } else if (errorCode == MicrosoftService.XboxAuthorizationException.BANNED) {
                return i18n("account.methods.microsoft.error.banned");
            } else {
                return i18n("account.methods.microsoft.error.unknown", errorCode);
            }
        } else if (exception instanceof MicrosoftService.XBox400Exception) {
            return i18n("account.methods.microsoft.error.wrong_verify_method");
        } else if (exception instanceof MicrosoftService.MinecraftJavaEditionLicenseNotFoundException) {
            return i18n("account.methods.microsoft.error.no_license");
        } else if (exception instanceof MicrosoftService.MinecraftJavaEditionProfileNotFoundException) {
            return i18n("account.methods.microsoft.error.no_character");
        } else if (exception instanceof MicrosoftService.NoXuiException) {
            return i18n("account.methods.microsoft.error.add_family");
        } else if (exception instanceof OAuthServer.MicrosoftAuthenticationNotSupportedException) {
            return i18n("account.methods.microsoft.snapshot");
        } else if (exception instanceof OAuthAccount.WrongAccountException) {
            return i18n("account.failed.wrong_account");
        } else if (exception.getClass() == AuthenticationException.class) {
            return exception.getLocalizedMessage();
        } else {
            return exception.getClass().getName() + ": " + exception.getLocalizedMessage();
        }
    }
}
