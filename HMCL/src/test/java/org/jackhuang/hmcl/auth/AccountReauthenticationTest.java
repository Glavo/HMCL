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

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies interactive credentials remain detached until explicitly accepted.
@NotNullByDefault
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

    /// Credentials stay detached until accepted and preserve the original identity.
    @Test
    public void acceptsDetachedCredentialsForSameAccount() throws Exception {
        UUID profile = UUID.randomUUID();
        MicrosoftAccount original = new MicrosoftAccount(AccountID.generate(), profile, "Player", session(profile, "old-token"));
        MicrosoftStub protocol = new MicrosoftStub(session(profile, "new-token"));
        AccountService service = new AccountService(new UnusedArtifacts(), protocol);
        OAuth.Callback callback = new OAuthServer.Factory();
        AuthenticationResult candidate = service.reauthenticateMicrosoftAccount(original, OAuth.GrantFlow.DEVICE, callback);
        assertSame(callback, protocol.receivedCallback);
        assertSame(original, candidate.account());
        assertEquals("old-token", original.requireSession().accessToken());
        assertFalse(original.isAuthenticated());
        assertSame(original, candidate.accept());
        assertEquals("new-token", original.requireSession().accessToken());
        assertTrue(original.isAuthenticated());
    }

    /// Wrong profiles cannot overwrite accepted credentials.
    @Test
    public void rejectsDifferentIdentityWithoutChangingOriginal() throws Exception {
        UUID profile = UUID.randomUUID();
        MicrosoftSession initial = session(profile, "old-token");
        MicrosoftAccount original = new MicrosoftAccount(AccountID.generate(), profile, "Player", initial);
        AccountService service = new AccountService(new UnusedArtifacts(), new MicrosoftStub(session(UUID.randomUUID(), "new-token")));
        assertThrows(WrongAccountException.class,
                () -> service.reauthenticateMicrosoftAccount(original, OAuth.GrantFlow.DEVICE, new OAuthServer.Factory()));
        assertSame(initial, original.getSession());
        assertFalse(original.isAuthenticated());
    }

    /// Later accepted credentials and explicit invalidation reject older authentication attempts.
    @Test
    public void rejectsObsoleteResults() throws Exception {
        UUID profile = UUID.randomUUID();
        MicrosoftAccount original = new MicrosoftAccount(AccountID.generate(), profile, "Player", session(profile, "old"));
        AccountService service = new AccountService(new UnusedArtifacts(), new MicrosoftStub(session(profile, "new")));
        AuthenticationResult first = service.reauthenticateMicrosoftAccount(original, OAuth.GrantFlow.DEVICE, new OAuthServer.Factory());
        AuthenticationResult second = service.reauthenticateMicrosoftAccount(original, OAuth.GrantFlow.DEVICE, new OAuthServer.Factory());
        second.accept();
        assertThrows(IllegalStateException.class, first::accept);
        AuthenticationResult pending = service.reauthenticateMicrosoftAccount(original, OAuth.GrantFlow.DEVICE, new OAuthServer.Factory());
        original.invalidateAuthenticationResults();
        assertThrows(IllegalStateException.class, pending::accept);
        assertEquals("new", original.requireSession().accessToken());
    }

    /// External credentials update display data without changing the server, login name, or profile identity.
    @Test
    public void preservesExternalIdentityOnAcceptance() throws Exception {
        UUID profile = UUID.randomUUID();
        AuthlibInjectorServer server = new AuthlibInjectorServer("https://example.invalid/auth/");
        AuthlibInjectorAccount original = new AuthlibInjectorAccount(AccountID.generate(), server, "user", profile, "Player",
                new YggdrasilSession("client", "old-token", new GameProfile(profile, "Player"), null, null));
        YggdrasilSession replacement = new YggdrasilSession("client", "new-token", new GameProfile(profile, "Renamed"), null, null);
        new AuthenticationResult(original, null, replacement, original.getAuthenticationRevision()).accept();
        assertSame(replacement, original.getSession());
        assertEquals("Renamed", original.getProfileName());
        assertSame(server, original.getServer());
        assertEquals("user", original.getLoginName());
        assertEquals(profile, original.getProfileID());
    }
}
