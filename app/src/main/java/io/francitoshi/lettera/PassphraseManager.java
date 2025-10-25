/*
 *  PassphraseManager.java
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
    
    public static char[] getPassphrase(boolean debug)
    {
        VirtualConsole console = AbstractConsole.getInstance(debug);
        return console.readPassword("passphrase:");
    }
    
    public static char[] createPassphrase(boolean mockConsole)
    {
        StrongPassword strongPassword = new StrongPassword(MIN_PASS_SIZE);
        VirtualConsole console = AbstractConsole.getInstance(mockConsole);
        System.out.println("Hello, i'm lettera!!!\n");
        System.out.println("You need a safe passphrase to keep your data safe. Let's create a good one.");
        System.out.println("16+ characters, just 4 or 5 random words would be enough.");
        System.out.println();
        while(true)
        {
            char[] passphrase=console.readPassword("passphrase:");
            if(passphrase.length<MIN_PASS_SIZE)
            {
                System.err.println("Too short: "+passphrase.length+" < "+MIN_PASS_SIZE);
            }
            int score = strongPassword.analyze(passphrase);
            StrongPassword.Level level = StrongPassword.getLevel(score);
            if(level.ordinal()<StrongPassword.Level.Good.ordinal())
            {
                System.err.println(level);
                continue;
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
