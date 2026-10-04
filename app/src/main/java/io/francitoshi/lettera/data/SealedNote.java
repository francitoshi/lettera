/*
 * Copyright (C) 2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.lettera.data;

import java.io.Serializable;

public class SealedNote implements Serializable, Comparable<SealedNote>
{
    public final String id;
    public final boolean mine;
    public final String innerEmail;
    public final String innerFingerprint;
    public final String outerEmail;
    public final String outerFingerprint;
    public final String text;
    
    public SealedNote(String id, boolean mine, String innerAddress, String innerKeyId, String outerAddress, String outerKeyId, String text)
    {
        this.id = id;
        this.mine = mine;
        this.innerEmail = innerAddress;
        this.innerFingerprint = innerKeyId;
        this.outerEmail = outerAddress;
        this.outerFingerprint = outerKeyId;
        this.text = text;
    }

    @Override
    public int compareTo(SealedNote other)
    {
        return this.id.compareTo(other.id);
    }
    
}
