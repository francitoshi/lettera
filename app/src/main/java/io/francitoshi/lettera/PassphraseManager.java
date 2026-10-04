/*
 * Copyright (C) 2025-2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.lettera;

import io.nut.base.crypto.gpg.PASS;
import io.nut.base.io.console.AbstractConsole;
import io.nut.base.io.console.VirtualConsole;
import io.nut.base.security.StrongPassword;
import java.util.Arrays;

/**
 *
 * @author franci
 */
public class PassphraseManager
{
    static final int MIN_PASS_SIZE = 16;
    
    public static String getUsername(boolean allowMock)
    {
        VirtualConsole console = AbstractConsole.getInstance(allowMock);
        return console.readLine("username:").trim();
    }
    public static char[] getPassphrase(boolean allowMock)
    {
        VirtualConsole console = AbstractConsole.getInstance(allowMock);
        return console.readPassword("passphrase:");
    }
    
    public static char[] getPassPath(boolean allowMock)
    {
        VirtualConsole console = AbstractConsole.getInstance(allowMock);
        String path = console.readLine("pass-path:");
        return PASS.getKey(path).toCharArray();
    }
    
    public static char[] createPassphrase(boolean mockConsole)
    {
        StrongPassword strongPassword = new StrongPassword(MIN_PASS_SIZE);
        VirtualConsole console = AbstractConsole.getInstance(mockConsole);
        System.out.println("Hello, i'm lettera!!!\n");
        System.out.println("You need a safe passphrase to keep your data safe. Let's create a good one.");
        System.out.println("16+ characters, just 4 or 5 random words would be enough.");
        System.out.println("left black to use a pass-path");
        System.out.println();
        while(true)
        {
            boolean passpath = false;
            char[] passphrase=console.readPassword("passphrase:");
            if(passphrase.length==0)
            {
                passpath = true;
            }
            if(passphrase.length<MIN_PASS_SIZE)
            {
                System.err.println("Too short: "+passphrase.length+" < "+MIN_PASS_SIZE);
                continue;
            }
            int score = strongPassword.analyze(passphrase);
            StrongPassword.Level level = StrongPassword.getLevel(score);
            if(level.ordinal()<StrongPassword.Level.Good.ordinal())
            {
                System.err.println(level);
                continue;
            }
            if(passpath)
            {
                return passphrase;
            }
            char[] passphrase2=console.readPassword("retype passphrase:");
            if(Arrays.compare(passphrase, passphrase2)==0)
            {
                Arrays.fill(passphrase2,'\0');
                return passphrase;
            }
        }
    }
//    public static char[] changePassphrase(char[] passphrase)
//    {
//        char[] pass = getPassphrase(true);
//        if(!Arrays.equals(passphrase, pass))
//        {
//            return null;
//        }
//        pass = createPassphrase(false);
//        if(Arrays.equals(passphrase, pass))
//        {
//            
//        }        
//    }    
}
