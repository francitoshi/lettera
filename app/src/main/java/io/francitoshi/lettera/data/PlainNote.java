/*
 * Copyright (C) 2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.lettera.data;

import io.nut.base.util.LongNonce;
import java.io.Serializable;

public class PlainNote implements Serializable, Comparable<PlainNote>
{
    private static final LongNonce NONCE = LongNonce.getEpochSecondInstance();
    
    public final long id;
    public final long created;
    public final boolean mine;
    public final String innerEmail;
    public final String innerFingerprint;
    public final String outerEmail;
    public final String outerFingerprint;
    public final String text;
    private volatile long showed;
    private volatile long stored;
    private volatile long sent;
    
    public PlainNote(long id, long created, boolean mine, String innerEmail, String innerFingerprint, String outerEmail, String outerFingerprint, String text, long showed, long stored, long sent)
    {
        this.id = id!=0 ? id : NONCE.get();
        this.created = created;
        this.mine = mine;
        this.innerEmail = innerEmail;
        this.innerFingerprint = innerFingerprint;
        this.outerEmail = outerEmail;
        this.outerFingerprint = outerFingerprint;
        this.text = text;
        this.showed = showed;
        this.stored = stored;
        this.sent = sent;
    }

    public PlainNote(long id, long created, boolean mine, String innerEmail, String innerFingerprint, String outerEmail, String outerFingerprint, String text)
    {
        this.id = id!=0 ? id : NONCE.get();
        this.created = created;
        this.mine = mine;
        this.innerEmail = innerEmail;
        this.innerFingerprint = innerFingerprint;
        this.outerEmail = outerEmail;
        this.outerFingerprint = outerFingerprint;
        this.text = text;
    }

    public long getShowed()
    {
        return showed;
    }

    public void setShowed(long showed)
    {
        this.showed = showed;
    }

    public boolean isShowed()
    {
        return showed!=0;
    }

    public long getStored()
    {
        return stored;
    }

    public void setStored(long stored)
    {
        this.stored = stored;
    }

    public boolean isStored()
    {
        return stored!=0;
    }

    public long getSent()
    {
        return sent;
    }

    public void setSent(long sent)
    {
        this.sent = sent;
    }

    public boolean isSent()
    {
        return sent!=0;
    }

    public boolean isModified()
    {
        return (stored<created) && (stored<showed) && (stored<sent);
    }
    
    @Override
    public int compareTo(PlainNote other)
    {
        return Long.compare(this.id, other.id);
    }
    
}
