/*
 * Copyright (C) 2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
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
        Map<String, EmailPreset> providers = EmailProviders.load();

        for( Map.Entry<String, EmailPreset> entry : providers.entrySet())
        {
            String name = entry.getKey();
            EmailPreset value = entry.getValue();
            
            System.out.println("name: "+name);
            System.out.println("domains: "+value.getDomains());
            System.out.println("smtp: "+value.getSmtp());
            System.out.println("imap: "+value.getImap());
            System.out.println("pop3: "+value.getPop3());
            System.out.println();
        }
        
    }

}
