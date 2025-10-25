/*
 *  Chat.java
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

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

class Chat implements Serializable
{
    private static final long serialVersionUID = 1L;
    
    public final String id;
    public final String accountName;
    public final String accountAddress;
    public final String accountKeyid;
    public final String friendName;
    public final String friendAddress;
    public final String friendKeyid;
    public final char[] sharedSecret;

    public Chat(String accountName, String accountAddress, String accountKeyid, String friendName, String friendAddress, String friendKeyid, char[] sharedSecret)
    {
        this.id = accountName+"-"+friendName;
        this.accountName = accountName;
        this.accountAddress = accountAddress;
        this.accountKeyid = accountKeyid;
        this.friendName = friendName;
        this.friendAddress = friendAddress;
        this.friendKeyid = friendKeyid;
        this.sharedSecret = sharedSecret;
    }

    public static Chat build(Account account, Friend friend, char[] sharedSecret)
    {
        return new Chat(account.name, account.address, account.keyid, friend.name, friend.address, friend.keyid, sharedSecret);
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 37 * hash + Objects.hashCode(this.id);
        hash = 37 * hash + Objects.hashCode(this.accountName);
        hash = 37 * hash + Objects.hashCode(this.accountAddress);
        hash = 37 * hash + Objects.hashCode(this.accountKeyid);
        hash = 37 * hash + Objects.hashCode(this.friendName);
        hash = 37 * hash + Objects.hashCode(this.friendAddress);
        hash = 37 * hash + Objects.hashCode(this.friendKeyid);
        hash = 37 * hash + Arrays.hashCode(this.sharedSecret);
        return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null)
        {
            return false;
        }
        if (getClass() != obj.getClass())
        {
            return false;
        }
        final Chat other = (Chat) obj;
        if (!Objects.equals(this.id, other.id))
        {
            return false;
        }
        if (!Objects.equals(this.accountName, other.accountName))
        {
            return false;
        }
        if (!Objects.equals(this.accountAddress, other.accountAddress))
        {
            return false;
        }
        if (!Objects.equals(this.accountKeyid, other.accountKeyid))
        {
            return false;
        }
        if (!Objects.equals(this.friendName, other.friendName))
        {
            return false;
        }
        if (!Objects.equals(this.friendAddress, other.friendAddress))
        {
            return false;
        }
        if (!Objects.equals(this.friendKeyid, other.friendKeyid))
        {
            return false;
        }
        return Arrays.equals(this.sharedSecret, other.sharedSecret);
    }


    public String diff(Chat other)
    {
        StringBuilder sb = new StringBuilder();
        if (!Objects.equals(this.accountName, other.accountName))
        {
            sb.append("accountName: ").append(this.accountName).append(" >> ").append(other.accountName).append('\n');
        }
        if (!Objects.equals(this.accountAddress, other.accountAddress))
        {
            sb.append("accountAddress: ").append(this.accountAddress).append(" >> ").append(other.accountAddress).append('\n');
        }
        if (!Objects.equals(this.accountKeyid, other.accountKeyid))
        {
            sb.append("accountKeyid: ").append(this.accountKeyid).append(" >> ").append(other.accountKeyid).append('\n');
        }
        if (!Objects.equals(this.friendName, other.friendName))
        {
            sb.append("friendName: ").append(this.friendName).append(" >> ").append(other.friendName).append('\n');
        }
        if (!Objects.equals(this.friendAddress, other.friendAddress))
        {
            sb.append("friendAddress: ").append(this.friendAddress).append(" >> ").append(other.friendAddress).append('\n');
        }
        if (!Objects.equals(this.friendKeyid, other.friendKeyid))
        {
            sb.append("friendKeyid: ").append(this.friendKeyid).append(" >> ").append(other.friendKeyid).append('\n');
        }
        if (!Arrays.equals(this.sharedSecret, other.sharedSecret))
        {
            sb.append("sharedSecret: ").append(this.sharedSecret).append(" >> ").append(other.sharedSecret).append('\n');
        }
        return sb.toString();
    }

    
}
