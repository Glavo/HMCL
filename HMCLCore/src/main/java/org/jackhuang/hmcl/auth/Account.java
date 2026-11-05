// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import java.util.Objects;
import java.util.UUID;

/// Holds a saved game identity and publishes state changes without depending on storage or UI frameworks.
@NotNullByDefault
public abstract class Account {
    /// The stable launcher entry identifier.
    private final AccountID accountID;
    /// The fixed game profile identifier within the authentication provider.
    private final UUID profileID;
    /// The last known display name, which may be empty for an unrestored online profile.
    private volatile String profileName;

    /// Invalidates detached authentication results when credentials change or the account is removed.
    private long authenticationRevision;
    /// Receives changes on the modifying thread; the owning account list installs this callback.
    private @Nullable Runnable changeListener;

    /// Creates an identity without requiring authentication credentials.
    protected Account(AccountID accountID, UUID profileID, String profileName) {
        this.accountID = Objects.requireNonNull(accountID);
        this.profileID = Objects.requireNonNull(profileID);
        this.profileName = Objects.requireNonNull(profileName);
    }

    /// Returns the stable launcher entry identifier.
    public final AccountID getAccountID() {
        return accountID;
    }

    /// Returns the fixed game profile identifier.
    public final UUID getProfileID() {
        return profileID;
    }

    /// Returns the last known display name without authenticating.
    public final String getProfileName() {
        return profileName;
    }

    /// Updates the last known name after credentials have been validated.
    protected final void updateProfileName(String name) {
        profileName = Objects.requireNonNull(name);
    }

    /// Returns the revision captured before starting interactive authentication.
    public final synchronized long getAuthenticationRevision() {
        return authenticationRevision;
    }

    /// Rejects outstanding authentication results without discarding the stored session.
    public final synchronized void invalidateAuthenticationResults() {
        authenticationRevision++;
    }

    /// Sets the owner's notification callback, or detaches it when the account leaves the list.
    public final synchronized void setChangeListener(@Nullable Runnable listener) {
        changeListener = listener;
    }

    /// Notifies the owner after configuration or display data changes.
    protected final synchronized void changed() {
        if (changeListener != null) changeListener.run();
    }

    /// Invalidates older results and publishes an accepted credential update.
    protected final synchronized void sessionChanged() {
        authenticationRevision++;
        changed();
    }

    /// Hashes the stable launcher identity independently of storage and credentials.
    @Override
    public final int hashCode() {
        return accountID.hashCode();
    }

    /// Compares saved entries by their stable launcher identity.
    @Override
    public final boolean equals(@Nullable Object other) {
        return this == other || other instanceof Account account && accountID.equals(account.accountID);
    }

    /// Describes the identity without including credentials.
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[accountID=" + accountID + ", profileID=" + profileID + "]";
    }
}
