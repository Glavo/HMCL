// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountID;
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
    public void installSettings() throws ReflectiveOperationException {
        replaceSetting("gameAccountPrivateData", privateData);
        replaceSetting("userGameAccountPrivateData", new AccountPrivateData());
        replaceSetting("authlibInjectorServers", new AuthlibInjectorServerList());
    }

    /// Preserves a setting before replacing it for this test.
    private void replaceSetting(String name, Object replacement) throws ReflectiveOperationException {
        var field = SettingsManager.class.getDeclaredField(name);
        field.setAccessible(true);
        previousSettings.put(name, field.get(null));
        field.set(null, replacement);
    }

    /// Restores every setting changed by this test.
    @AfterEach
    public void restoreSettings() throws ReflectiveOperationException {
        for (var entry : previousSettings.entrySet()) {
            var field = SettingsManager.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            field.set(null, entry.getValue());
        }
    }

    /// Serializes an account through the production path and extracts one JSON component.
    private static JsonObject serialize(Account account, String component) throws ReflectiveOperationException {
        var method = Accounts.class.getDeclaredMethod("serializeAccount", Account.class);
        method.setAccessible(true);
        Object serialized = Objects.requireNonNull(method.invoke(null, account));
        var accessor = serialized.getClass().getDeclaredMethod(component);
        accessor.setAccessible(true);
        return (JsonObject) Objects.requireNonNull(accessor.invoke(serialized));
    }

    /// Restores a metadata record through the production path using the isolated private data store.
    private static @Nullable Account parse(JsonObject metadata) throws ReflectiveOperationException {
        var method = Accounts.class.getDeclaredMethod("parseAccount", JsonObject.class, boolean.class);
        method.setAccessible(true);
        return (Account) method.invoke(null, metadata, true);
    }

    /// Checks all account types retain identity, skin settings, and credentials across persistence.
    @Test
    public void roundTripsSupportedAccounts() throws ReflectiveOperationException {
        UUID profileID = UUID.randomUUID();
        AuthlibInjectorServer server = new AuthlibInjectorServer("https://example.invalid/auth/");
        for (Account original : List.of(
                new OfflineAccount(AccountID.generate(), "Player", profileID, new Skin(Skin.Type.ALEX, null, null, null, null)),
                new MicrosoftAccount(AccountID.generate(), new MicrosoftSession("Bearer", "access", 123L, "refresh",
                        new MicrosoftSession.User("user"), new MicrosoftSession.GameProfile(profileID, "Player")), true),
                new AuthlibInjectorAccount(AccountID.generate(), server, "login@example.invalid",
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
    public void restoresOfflineUUIDDefault() throws ReflectiveOperationException {
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
    public void retainsCachedProfileAndRejectsUnknownTypes() throws ReflectiveOperationException {
        AuthlibInjectorAccount account = new AuthlibInjectorAccount(AccountID.generate(),
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
}
