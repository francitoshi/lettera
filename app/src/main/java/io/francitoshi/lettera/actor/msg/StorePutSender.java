/*
 * Copyright (C) 2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.lettera.actor.msg;

import io.francitoshi.lettera.data.Sender;
import java.io.Serializable;

public class StorePutSender implements Serializable {
    private static final long serialVersionUID = 1L;
    public final Sender sender;

    public StorePutSender(Sender sender) {
        this.sender = sender;
    }
}
