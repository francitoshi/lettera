/*
 * Copyright (C) 2025-2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.lettera;

import io.francitoshi.lettera.data.PlainNote;
import io.francitoshi.lettera.data.Friend;
import io.francitoshi.lettera.data.Sender;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.h2.mvstore.MVStore;

/**
 *
 * @author franci
 */
public class LetteraDb implements Closeable
{
    private final Object lock = new Object();
    private final MVStore store;
    private final Map<String, Sender> sender;
    private final Map<String, Long> counters;
    private final Map<String, Friend> friends;

    public LetteraDb(File file, char[] passphrase)
    {
        this.store = new MVStore.Builder().fileName(file.getAbsolutePath()).compress().recoveryMode().encryptionKey(passphrase).open();
        this.sender = this.store.openMap("sender");
        this.counters = this.store.openMap("counters");
        this.friends = this.store.openMap("friends");
    }

    @Override
    public void close() throws IOException
    {
        synchronized(lock)
        {
            store.close();
        }
    }

    public void putSender(Sender value)
    {
        synchronized(lock)
        {
            sender.put("", value);
        }
    }
    public Sender getSender()
    {
        synchronized(lock)
        {
            return sender.get("");
        }
    }
    
    public long incrementAndGet(String key)
    {
        synchronized(lock)
        {
            Long value = counters.get(key);
            value = value!=null ? value + 1L : 1L;
            counters.put(key, value);
            return value;
        }
    }
    
    public void putFriend(Friend value)
    {
        synchronized(lock)
        {
            friends.put(value.email, value);
        }
    }

    public Friend getFriend(String id)
    {
        synchronized(lock)
        {
            return friends.get(id);
        }
    }
    public int getFriendsCount()
    {
        synchronized(lock)
        {
            return friends.size();
        }
    }
    
    public Friend[] getFriends()
    {
        synchronized(lock)
        {
            return friends.values().toArray(new Friend[0]);
        }
    }
    
    public Map<Long,PlainNote> openChat(String id)
    {
        synchronized(lock)
        {
            return store.openMap("chat:"+id);
        }
    }

    public final void commit()
    {
        synchronized(lock)
        {
            store.commit();
        }
    }
    
}
