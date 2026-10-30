// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth;

import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Identifies supported account types using their persisted identifiers.
@NotNullByDefault
public enum AccountType {
    /// An offline player identity.
    OFFLINE("offline"),
    /// A Microsoft-authenticated Minecraft identity.
    MICROSOFT("microsoft"),
    /// An identity authenticated by an external Yggdrasil server.
    AUTHLIB_INJECTOR("authlibInjector");

    /// The stable identifier used in account storage and launcher preferences.
    private final String id;

    /// Associates a type with its persisted identifier.
    AccountType(String id) {
        this.id = id;
    }

    /// Returns the stable persisted identifier.
    public String id() {
        return id;
    }

    /// Resolves a persisted identifier.
    ///
    /// @throws IllegalArgumentException if the identifier is `null` or unsupported
    public static AccountType fromId(@Nullable String id) {
        for (AccountType type : values()) {
            if (type.id.equals(id)) return type;
        }
        throw new IllegalArgumentException("Unsupported account type: " + id);
    }

    /// Identifies the supported concrete account type.
    ///
    /// @throws IllegalArgumentException if the account type is unsupported
    public static AccountType of(Account account) {
        if (account instanceof OfflineAccount) return OFFLINE;
        if (account instanceof MicrosoftAccount) return MICROSOFT;
        if (account instanceof AuthlibInjectorAccount) return AUTHLIB_INJECTOR;
        throw new IllegalArgumentException("Unsupported account class: " + account.getClass().getName());
    }
}
