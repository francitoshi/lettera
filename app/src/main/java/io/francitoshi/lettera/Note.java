/*
 *  Note.java
 *
 *  Copyright (c) 2025 francitoshi@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  Report bugs or new features to: francitoshi@gmail.com
 */
package io.francitoshi.lettera;

import io.nut.base.util.LongNonce;
import java.io.Serializable;

/**
 *
 * @author franci
 */
public class Note implements Serializable, Comparable<Note>
{
    public static final LongNonce NONCE = LongNonce.getCurrentMillisInstance(); //666 cambiar por EpochSecond
    
    public final long id;
    public final long epochSecond;
    public final String session;
    public final String from;
    public final String to;
    public final String keyFrom;
    public final String keyTo;
    public final long received;
    public final String text;
    private volatile long sent;
    private volatile boolean stored;
    private volatile boolean showed;
    
    public Note(long id, long epochSecond, String session, String from, String to, String keyFrom, String keyTo, long received, long sent, String text)
    {
        this.id = id;
        this.epochSecond = epochSecond;
        this.session = session;
        this.from = from;
        this.to = to;
        this.keyFrom = keyFrom;
        this.keyTo = keyTo;
        this.received = received;
        this.sent = sent;
        this.text = text;
    }
 
    public long getSent()
    {
        return sent;
    }

    public void setSent(long sent)
    {
        this.sent = sent;
    }

    public boolean isStored()
    {
        return stored;
    }

    public void setStored(boolean stored)
    {
        this.stored = stored;
    }

    public boolean isShowed()
    {
        return showed;
    }

    public void setShowed(boolean showed)
    {
        this.showed = showed;
    }

    @Override
    public int compareTo(Note other)
    {
        return Long.compare(this.id, other.id);
    }
    
    
}
