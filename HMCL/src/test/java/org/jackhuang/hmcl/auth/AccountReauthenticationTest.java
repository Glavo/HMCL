// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth;

import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorArtifactInfo;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorArtifactProvider;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftService;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftSession;
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilSession;
import org.jackhuang.hmcl.game.OAuthServer;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies interactive credentials remain detached until explicitly accepted.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class AccountReauthenticationTest {
    /// Fails if a test unexpectedly requests injector resources.
    @NotNullByDefault
    private static final class UnusedArtifacts implements AuthlibInjectorArtifactProvider {
        @Override
        public AuthlibInjectorArtifactInfo getArtifactInfo() {
            throw new AssertionError("Unexpected artifact request");
        }

        @Override
        public Optional<AuthlibInjectorArtifactInfo> getArtifactInfoImmediately() {
            throw new AssertionError("Unexpected artifact request");
        }
    }

    /// Supplies deterministic Microsoft authentication without starting OAuth or contacting a server.
    @NotNullByDefault
    private static final class MicrosoftStub extends MicrosoftService {
        /// The session returned by interactive authentication.
        private final MicrosoftSession response;
        /// Records the operation-specific callback passed to authentication.
        private @Nullable OAuth.Callback receivedCallback;

        /// Creates a protocol stub for one response.
        private MicrosoftStub(MicrosoftSession response) {
            super(new OAuthServer.Factory());
            this.response = response;
        }

        @Override
        public MicrosoftSession authenticate(OAuth.GrantFlow flow, OAuth.Callback callback) {
            receivedCallback = callback;
            return response;
        }
    }

    /// Creates a complete session for a test profile.
    private static MicrosoftSession session(UUID profile, String token) {
        return new MicrosoftSession("Bearer", token, Long.MAX_VALUE, "refresh",
                new MicrosoftSession.User("user"), new MicrosoftSession.GameProfile(profile, "Player"));
    }

    /// Verifies acceptance updates the original object without changing identity or storage settings.
    @Test
    public void acceptsDetachedCredentialsForSameAccount() throws Exception {
        UUID profile = UUID.randomUUID();
        MicrosoftAccount original = new MicrosoftAccount(AccountID.generate(), session(profile, "old-token"), false);
        original.setPortable(true);
        MicrosoftStub protocol = new MicrosoftStub(session(profile, "new-token"));
        AccountService service = new AccountService(new UnusedArtifacts(), protocol);
        OAuth.Callback callback = new OAuthServer.Factory();
        MicrosoftAccount candidate = service.reauthenticateMicrosoftAccount(original, OAuth.GrantFlow.DEVICE, callback);
        assertSame(callback, protocol.receivedCallback);
        assertNotSame(original, candidate);
        assertEquals(original.getAccountID(), candidate.getAccountID());
        assertTrue(candidate.isPortable());
        assertEquals("old-token", original.getSession().accessToken());
        assertFalse(original.isAuthenticated());
        service.acceptReauthentication(original, candidate);
        assertEquals("new-token", original.getSession().accessToken());
        assertTrue(original.isAuthenticated());
        assertTrue(original.isPortable());
    }

    /// Verifies wrong profiles and unrelated account IDs cannot overwrite the original credentials.
    @Test
    public void rejectsDifferentIdentityWithoutChangingOriginal() {
        UUID profile = UUID.randomUUID();
        MicrosoftSession initial = session(profile, "old-token");
        MicrosoftAccount original = new MicrosoftAccount(AccountID.generate(), initial, false);
        AccountService service = new AccountService(new UnusedArtifacts(), new MicrosoftStub(session(UUID.randomUUID(), "new-token")));
        assertThrows(OAuthAccount.WrongAccountException.class,
                () -> service.reauthenticateMicrosoftAccount(original, OAuth.GrantFlow.DEVICE, new OAuthServer.Factory()));
        assertThrows(IllegalArgumentException.class, () -> service.acceptReauthentication(original,
                new MicrosoftAccount(AccountID.generate(), session(profile, "another-token"), true)));
        assertSame(initial, original.getSession());
        assertFalse(original.isAuthenticated());
    }

    /// Verifies external acceptance checks the server and login identity before replacing credentials.
    @Test
    public void preservesExternalIdentityOnAcceptance() throws Exception {
        UUID profile = UUID.randomUUID();
        AuthlibInjectorServer server = new AuthlibInjectorServer("https://example.invalid/auth/");
        AuthlibInjectorAccount original = new AuthlibInjectorAccount(AccountID.generate(), server, "user",
                new YggdrasilSession("client", "old-token", new GameProfile(profile, "Player"), null, null));
        original.setPortable(true);
        YggdrasilSession replacement = new YggdrasilSession("client", "new-token", new GameProfile(profile, "Renamed"), null, null);
        AccountService service = new AccountService(new UnusedArtifacts(), new MicrosoftStub(session(profile, "unused")));
        assertThrows(IllegalArgumentException.class, () -> service.acceptReauthentication(original,
                new AuthlibInjectorAccount(original.getAccountID(), new AuthlibInjectorServer("https://other.invalid/"), "user", replacement)));
        assertThrows(IllegalArgumentException.class, () -> service.acceptReauthentication(original,
                new AuthlibInjectorAccount(original.getAccountID(), server, "other-user", replacement)));
        assertEquals("old-token", original.getSession().getAccessToken());
        AuthlibInjectorAccount candidate = new AuthlibInjectorAccount(original.getAccountID(), server, "user", replacement);
        service.acceptReauthentication(original, candidate);
        assertSame(replacement, original.getSession());
        assertEquals("Renamed", original.getProfileName());
        assertEquals(profile, original.getProfileID());
        assertTrue(original.isPortable());
    }

}
