/*
 * Copyright (C) 2025-2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.lettera;

import io.nut.base.crypto.SecureWrapper;
import io.nut.base.commons.lang.Chars;
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
        byte[] pass = Chars.bytesUTF8(password);
        try
        {
            return wrapper.wrap(pass, purposeName);
        }
        finally
        {
            Arrays.fill(pass, (byte)0);
        }
    }
    
    public char[] unwrapKey(String purpose, String name, String wrapped)
    {
        String purposeName = purpose+"+"+name;
        byte[] pass = wrapper.unwrap(wrapped, purposeName);
        try
        {
            return Chars.charsUTF8(pass);
        }
        finally
        {
            Arrays.fill(pass, (byte)0);
        }
    }
    
}
