/*
 *  EmailProvidersTest.java
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
package io.francitoshi.lettera.email;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class EmailProvidersTest
{

    /**
     * Test of getProvider method, of class EmailProviderLoader.
     */
    @Test
    public void testGetProvider() throws Exception
    {
        Map<String, EmailSettings> providers = EmailProviders.load();

        for( Map.Entry<String, EmailSettings> entry : providers.entrySet())
        {
            String name = entry.getKey();
            EmailSettings value = entry.getValue();
            
            System.out.println("name: "+name);
            System.out.println("domains: "+value.getDomains());
            System.out.println("smtp: "+value.getSmtp());
            System.out.println("imap: "+value.getImap());
            System.out.println("pop3: "+value.getPop3());
            System.out.println();
        }
        
    }

}
