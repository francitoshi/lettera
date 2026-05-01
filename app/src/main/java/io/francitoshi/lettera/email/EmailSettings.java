/*
 *  EmailSettings.java
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

public class EmailSettings
{

    private String domains;
    private ServerSettings smtp;
    private ServerSettings imap;
    private ServerSettings pop3;

    public EmailSettings()
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
}
