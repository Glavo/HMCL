// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.auth.AuthenticationResult;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftSession;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.offline.Skin;
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilSession;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies account persistence using isolated in-memory settings without authentication or disk writes.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class AccountsPersistenceTest {
    /// Private data used only by the current test.
    private final AccountPrivateData privateData = new AccountPrivateData();
    /// Settings replaced by each test and restored afterward.
    private final Map<String, @Nullable Object> previousSettings = new HashMap<>();

    /// Initializes account services with an unused injector path instead of a packaged artifact.
    @BeforeAll
    public static void initializeAccounts() {
        String property = "hmcl.authlibinjector.location";
        @Nullable String previous = System.getProperty(property);
        System.setProperty(property, "unused-test-authlib-injector.jar");
        try {
            Accounts.getAccounts();
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    /// Installs stores without autosave listeners so tests never touch the user's files.
    @BeforeEach
    public void installSettings() throws Exception {
        replaceSetting("gameAccountPrivateData", privateData);
        replaceSetting("userGameAccountPrivateData", new AccountPrivateData());
        replaceSetting("authlibInjectorServers", new AuthlibInjectorServerList());
    }

    /// Preserves a setting before replacing it for this test.
    private void replaceSetting(String name, Object replacement) throws Exception {
        var field = SettingsManager.class.getDeclaredField(name);
        field.setAccessible(true);
        previousSettings.put(name, field.get(null));
        field.set(null, replacement);
    }

    /// Restores every setting changed by this test.
    @AfterEach
    public void restoreSettings() throws Exception {
        onFx(() -> { for (Account account : List.copyOf(Accounts.getAccounts())) Accounts.remove(account); return true; });
        for (var entry : previousSettings.entrySet()) {
            var field = SettingsManager.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            field.set(null, entry.getValue());
        }
    }

    /// Serializes an account through the production path and extracts one JSON component.
    private static JsonObject serialize(Account account, String component) throws Exception {
        var method = Accounts.class.getDeclaredMethod("serializeAccount", Account.class);
        method.setAccessible(true);
        Object serialized = Objects.requireNonNull(method.invoke(null, account));
        var accessor = serialized.getClass().getDeclaredMethod(component);
        accessor.setAccessible(true);
        return (JsonObject) Objects.requireNonNull(accessor.invoke(serialized));
    }

    /// Restores a metadata record through the production path using the isolated private data store.
    private static @Nullable Account parse(JsonObject metadata) throws Exception {
        var method = Accounts.class.getDeclaredMethod("parseAccount", JsonObject.class, boolean.class);
        method.setAccessible(true);
        return onFx(() -> {
            for (Account account : List.copyOf(Accounts.getAccounts())) {
                if (account.getAccountID().equals(AccountMetadataStore.readAccountID(metadata))) Accounts.remove(account);
            }
            return (Account) method.invoke(null, metadata, true);
        });
    }

    /// Runs list mutations on the same thread as production account management.
    private static <T> T onFx(java.util.concurrent.Callable<T> operation) throws Exception {
        java.util.concurrent.FutureTask<T> task = new java.util.concurrent.FutureTask<>(operation);
        javafx.application.Platform.runLater(task);
        return task.get(10, java.util.concurrent.TimeUnit.SECONDS);
    }

    /// Registers an account without authenticating it.
    private static Account register(Account account) throws Exception {
        return onFx(() -> { Accounts.add(account, false); return account; });
    }

    /// Creates an identity with previously saved Microsoft credentials.
    private static Account microsoft(MicrosoftSession session) throws Exception {
        return register(new MicrosoftAccount(AccountID.generate(), session.profile().id(), session.profile().name(), session));
    }

    /// Creates an identity with previously saved external credentials.
    private static Account injector(AuthlibInjectorServer server, String login, YggdrasilSession session) throws Exception {
        return register(new AuthlibInjectorAccount(AccountID.generate(), server, login,
                session.getSelectedProfile().getId(), session.getSelectedProfile().getName(), session));
    }

    /// Checks all account types retain identity, skin settings, and credentials across persistence.
    @Test
    public void roundTripsSupportedAccounts() throws Exception {
        UUID profileID = UUID.randomUUID();
        AuthlibInjectorServer server = new AuthlibInjectorServer("https://example.invalid/auth/");
        for (Account original : List.of(
                register(new OfflineAccount(AccountID.generate(), "Player", profileID, new Skin(Skin.Type.ALEX, null, null, null, null))),
                microsoft(new MicrosoftSession("Bearer", "access", 123L, "refresh",
                        new MicrosoftSession.User("user"), new MicrosoftSession.GameProfile(profileID, "Player"))),
                injector(server, "login@example.invalid",
                        new YggdrasilSession("client", "access", new GameProfile(profileID, "Player"), null, Map.of("key", "value"))))) {
            JsonObject metadata = serialize(original, "metadata");
            JsonObject credentials = serialize(original, "privateData");
            privateData.getPrivateData().put(original.getAccountID(), credentials);
            Account restored = Objects.requireNonNull(parse(metadata));
            assertEquals(original.getAccountID(), restored.getAccountID());
            assertEquals(original.getProfileID(), restored.getProfileID());
            assertEquals(original.getProfileName(), restored.getProfileName());
            assertEquals(metadata, serialize(restored, "metadata"));
            assertEquals(credentials, serialize(restored, "privateData"));
            if (restored instanceof MicrosoftAccount microsoft) assertFalse(microsoft.isAuthenticated());
        }
    }

    /// Checks legacy offline records may omit the derived UUID and skin.
    @Test
    public void restoresOfflineUUIDDefault() throws Exception {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("accountID", AccountID.generate().toString());
        metadata.addProperty("type", "offline");
        metadata.addProperty("profileName", "Player");
        Account restored = Objects.requireNonNull(parse(metadata));
        assertEquals(OfflineAccount.getUUIDFromUserName("Player"), restored.getProfileID());
        assertNull(((OfflineAccount) restored).getSkin());
        assertTrue(SettingsManager.getAuthlibInjectorServers().isEmpty());
    }

    /// Checks restored profile properties survive another save and malformed records are rejected.
    @Test
    public void retainsCachedProfileAndRejectsUnknownTypes() throws Exception {
        Account account = injector(
                new AuthlibInjectorServer("https://example.invalid/auth/"), "user",
                new YggdrasilSession("client", "access", new GameProfile(UUID.randomUUID(), "Player"), null, null));
        JsonObject metadata = serialize(account, "metadata");
        JsonObject credentials = serialize(account, "privateData");
        JsonObject properties = new JsonObject();
        properties.addProperty("textures", "cached-textures");
        credentials.add("profileProperties", properties);
        privateData.getPrivateData().put(account.getAccountID(), credentials);
        Account restored = Objects.requireNonNull(parse(metadata));
        assertEquals(credentials, serialize(restored, "privateData"));
        metadata.addProperty("type", "unknown");
        assertNull(parse(metadata));
    }

    /// Missing private data keeps the saved identity available for interactive reauthentication.
    @Test
    public void retainsIdentityWithoutCredentials() throws Exception {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("accountID", AccountID.generate().toString());
        metadata.addProperty("type", "microsoft");
        metadata.addProperty("profileID", UUID.randomUUID().toString());
        Account restored = Objects.requireNonNull(parse(metadata));
        assertInstanceOf(MicrosoftAccount.class, restored);
        assertNull(((MicrosoftAccount) restored).getSession());
        assertEquals(metadata, serialize(restored, "metadata"));
    }

    /// Account changes update the stable read-only JavaFX view and invalidate profile bindings.
    @Test
    public void projectsStorageAndProfileChangesToJavaFX() throws Exception {
        java.util.concurrent.FutureTask<Boolean> task = new java.util.concurrent.FutureTask<>(() -> {
            var view = Accounts.getAccounts();
            assertSame(view, Accounts.getAccounts());
            MicrosoftAccount account = new MicrosoftAccount(AccountID.generate(), UUID.randomUUID(), "Player");
            MicrosoftSession initial = new MicrosoftSession("Bearer", "old", Long.MAX_VALUE, "refresh",
                    new MicrosoftSession.User("user"), new MicrosoftSession.GameProfile(account.getProfileID(), "Player"));
            account.updateSession(initial);
            Accounts.add(account, false);
            assertTrue(view.contains(account));
            assertThrows(UnsupportedOperationException.class, () -> view.remove(account));
            javafx.beans.binding.StringBinding name = javafx.beans.binding.Bindings.createStringBinding(
                    account::getProfileName, Accounts.observable(account));
            assertEquals("Player", name.get());
            Accounts.setPortable(account, true);
            assertSame(account, view.getFirst());
            MicrosoftSession replacement = new MicrosoftSession("Bearer", "new", Long.MAX_VALUE, "refresh",
                    new MicrosoftSession.User("user"), new MicrosoftSession.GameProfile(account.getProfileID(), "Renamed"));
            new AuthenticationResult(account, replacement, null, account.getAuthenticationRevision()).accept();
            assertEquals("Renamed", name.get());
            name.dispose();
            AuthenticationResult pending = new AuthenticationResult(account, initial, null, account.getAuthenticationRevision());
            Accounts.remove(account);
            assertThrows(IllegalStateException.class, pending::accept);
            Accounts.add(account, false);
            assertThrows(IllegalStateException.class, pending::accept);
            Accounts.remove(account);
            assertFalse(view.contains(account));
            return true;
        });
        javafx.application.Platform.runLater(task);
        task.get(10, java.util.concurrent.TimeUnit.SECONDS);
    }
}
