/*
 *  KeyWrapper.java
 *
 *  Copyright (c) 2026 francitoshi@gmail.com
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

import io.nut.base.crypto.SecureWrapper;
import io.nut.base.util.Byter;
import java.util.Arrays;

public class KeyWrapper
{
    private final SecureWrapper wrapper;

    public KeyWrapper(SecureWrapper wrapper)
    {
        this.wrapper = wrapper;
    }
    
    public String wrapKey(String purpose, String name, char[] password)
    {
        String purposeName = purpose+"+"+name;
        byte[] pass = Byter.bytesUTF8(password);
        String wrapped = wrapper.wrap(pass, purposeName);
        Arrays.fill(pass, (byte)0);
        return wrapped;
    }
    
    public char[] unwrapKey(String purpose, String name, String wrapped)
    {
        String purposeName = purpose+"+"+name;
        byte[] pass = wrapper.unwrap(wrapped, purposeName);
        char[] password = Byter.charsUTF8(pass);
        Arrays.fill(pass, (byte)0);
        return password;
    }
    
}
