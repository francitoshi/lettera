/*
 *  LetteraDb.java
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
    private final Map<String, Account> accounts;
    private final Map<String, Friend> friends;
    private final Map<String, Chat> chats;

    public LetteraDb(File file, char[] passphrase)
    {
        this.store = new MVStore.Builder().fileName(file.getAbsolutePath()).compress().recoveryMode().encryptionKey(passphrase).open();
        this.accounts = this.store.openMap("accounts");
        this.friends = this.store.openMap("friends");
        this.chats = this.store.openMap("chats");
    }

    @Override
    public void close() throws IOException
    {
        synchronized(lock)
        {
            store.close();
        }
    }

    public Account[] getAccounts()
    {
        synchronized(lock)
        {
            return this.accounts.values().toArray(new Account[0]);
        }
    }
    
    public Friend[] getFriends()
    {
        synchronized(lock)
        {
            return this.friends.values().toArray(new Friend[0]);
        }
    }

    public Chat[] getChats()
    {
        synchronized(lock)
        {
            return this.chats.values().toArray(new Chat[0]);
        }
    }

    public void putAccount(Account value)
    {
        synchronized(lock)
        {
            accounts.put(value.name, value);
        }
    }
    public Account getAccount(String name)
    {
        synchronized(lock)
        {
            return accounts.get(name);
        }
    }
    
    public void putFriend(Friend value)
    {
        synchronized(lock)
        {
            friends.put(value.name, value);
        }
    }
    public Friend getFriend(String name)
    {
        synchronized(lock)
        {
            return friends.get(name);
        }
    }
    
    public void putChat(Chat value)
    {
        synchronized(lock)
        {
            chats.put(value.id, value);
        }
    }
    public Chat getChat(String id)
    {
        synchronized(lock)
        {
            return chats.get(id);
        }
    }
    
    public final void commit()
    {
        synchronized(lock)
        {
            store.commit();
        }
    }
    
    public Map<Long, Note> getNotes(String id)
    {
        synchronized(lock)
        {
            return this.store.openMap("notes-"+id);
        }
    }
    
}
