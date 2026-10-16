/*
 * Copyright (C) 2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.lettera.actor.msg;

import io.francitoshi.lettera.data.Friend;
import java.io.Serializable;

public class StoreFriendResp implements Serializable {
    private static final long serialVersionUID = 1L;
    public final Friend friend;

    public StoreFriendResp(Friend friend) {
        this.friend = friend;
    }
}
