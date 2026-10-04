/*
 * Copyright (C) 2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.lettera.email;

public class EmailPreset
{

    private String domains;
    private ServerSettings smtp;
    private ServerSettings imap;
    private ServerSettings pop3;

    public EmailPreset()
    {
    }

    public String getDomains()
    {
        return domains;
    }

    public void setDomains(String domains)
    {
        this.domains = domains;
    }

    public ServerSettings getSmtp()
    {
        return smtp;
    }

    public void setSmtp(ServerSettings smtp)
    {
        this.smtp = smtp;
    }

    public ServerSettings getImap()
    {
        return imap;
    }

    public void setImap(ServerSettings imap)
    {
        this.imap = imap;
    }

    public ServerSettings getPop3()
    {
        return pop3;
    }

    public void setPop3(ServerSettings pop3)
    {
        this.pop3 = pop3;
    }
    
    public boolean isDomain(String address)
    {
        address = address.toLowerCase();
        for(String domain : domains.split(","))
        {
            if(address.contains(domain))
            {
                return true;
            }
        }
        return false;
    }
}
