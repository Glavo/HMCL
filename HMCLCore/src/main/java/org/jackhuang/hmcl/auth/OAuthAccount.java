// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.UUID;

/// Base class for accounts authenticated through OAuth.
@NotNullByDefault
public abstract class OAuthAccount extends Account {
    /// Creates an OAuth account.
    ///
    /// @param accountID the stable account entry ID
    protected OAuthAccount(AccountID accountID) {
        super(accountID);
    }

    /// Exception thrown when refreshed OAuth credentials belong to a different account.
    public static class WrongAccountException extends AuthenticationException {
        /// The expected Minecraft profile ID.
        private final UUID expected;

        /// The actual Minecraft profile ID returned by the OAuth flow.
        private final UUID actual;

        /// Creates a wrong account exception.
        ///
        /// @param expected the expected Minecraft profile ID
        /// @param actual the actual Minecraft profile ID returned by the OAuth flow
        public WrongAccountException(UUID expected, UUID actual) {
            super("Expected account " + expected + ", but found " + actual);
            this.expected = expected;
            this.actual = actual;
        }

        /// Returns the expected Minecraft profile ID.
        public UUID getExpected() {
            return expected;
        }

        /// Returns the actual Minecraft profile ID returned by the OAuth flow.
        public UUID getActual() {
            return actual;
        }
    }
}
