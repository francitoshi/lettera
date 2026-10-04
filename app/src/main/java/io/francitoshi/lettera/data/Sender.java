/*
 * Copyright (C) 2025-2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.lettera.data;

import io.nut.base.net.Emails;
import java.io.Serializable;

public class Sender implements Serializable
{
    private static final long serialVersionUID = 1L;
    
    public final String email;
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
    public final String gpgKeyId;
    public final String gpgPassphrase;
    public final String proxy;

    public Sender(String email, boolean auth, boolean starttls, String smtpHost, int smtpPort, String imapHost, int imapPort, String pop3Host, int pop3Port, String username, String emailPass, String gpgKeyId, String gpgPass, String proxy)
    {
        this.email = email;
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
        this.gpgKeyId = gpgKeyId;
        this.gpgPassphrase = gpgPass;
        this.proxy = proxy;
    }

    public boolean isValid()
    {
        if(email==null || email.isEmpty() || !Emails.isValidEmail(email, true))
        {
            return false;
        }
        if(smtpHost==null || smtpHost.isEmpty())
        {
            return false;
        }
        if(smtpPort<=0)
        {
            return false;
        }

        if(imapHost==null || imapHost.isEmpty())
        {
            return false;
        }
        if(imapPort<=0)
        {
            return false;
        }

        if(username==null || username.isEmpty())
        {
            return false;
        }
        if(emailPass==null || emailPass.isEmpty())
        {
            return false;
        }
        return true;
    }
}
