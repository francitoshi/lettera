/*
 * Copyright (C) 2025-2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.lettera.data;

import java.io.Serializable;

/**
 *
 * @author franci
 */
public class Friend implements Serializable
{
    private static final long serialVersionUID = 1L;
    
    public final String name;
    public final String email;
    public final String fingerprint;
    public final String sharedSecret;
    public final String sessionKey;
    public final int trustLevel;

    public Friend(String name, String email, String fingerprint, String sharedSecret, String sessionKey, int trustLevel)
    {
        this.name = name;
        this.email = email;
        this.fingerprint = fingerprint;
        this.sharedSecret = sharedSecret;
        this.sessionKey = sessionKey;
        this.trustLevel = trustLevel;
    }

}
