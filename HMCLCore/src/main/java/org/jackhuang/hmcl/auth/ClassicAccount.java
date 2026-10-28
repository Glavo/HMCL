// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth;

/// Identifies accounts that authenticate with a login name and password.
public abstract class ClassicAccount extends Account {
    /// Creates a classic account.
    ///
    /// @param accountID the stable account entry ID
    protected ClassicAccount(AccountID accountID) {
        super(accountID);
    }

    /// Returns the login name used by the remote authentication service.
    public abstract String getLoginName();

}
