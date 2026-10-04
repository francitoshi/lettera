/*
 * Copyright (C) 2025-2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.lettera;

import java.io.Serializable;

public class Chat implements Serializable
{
    private static final long serialVersionUID = 1L;
    
    public final String name;
    public final String address;
    public final String keyid;
    public final String sharedSecret;

    public Chat(String name, String address, String keyid, String sharedSecret)
    {
        this.name = name;
        this.address = address;
        this.keyid = keyid;
        this.sharedSecret = sharedSecret;
    }
    
}
