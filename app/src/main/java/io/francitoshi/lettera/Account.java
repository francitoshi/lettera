/*
 *  Account.java
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

public class Account implements Serializable
{
    private static final long serialVersionUID = 1L;
    
    public final String name;
    public final String address;
    public final boolean auth;
    public final boolean starttls;
    public final String smtpHost;
    public final int smtpPort;
    public final String imapHost;
    public final int imapPort;
    public final String pop3Host;
    public final int pop3Port;
    public final String username;
    public final String emailPass;
    public final String keyid;
    public final String gpgPass;

    public Account(String name, String address, boolean auth, boolean starttls, String smtpHost, int smtpPort, String imapHost, int imapPort, String pop3Host, int pop3Port, String username, String emailPass, String keyId, String gpgPass)
    {
        this.name = name;
        this.address = address;
        this.auth = auth;
        this.starttls = starttls;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.imapHost = imapHost;
        this.imapPort = imapPort;
        this.pop3Host = pop3Host;
        this.pop3Port = pop3Port;
        this.username = username;
        this.emailPass = emailPass;
        this.keyid = keyId;
        this.gpgPass = gpgPass;
    }

  
}
