/*
 * Copyright (C) 2025-2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.lettera.email;

import io.francitoshi.lettera.KeyWrapper;
import static io.francitoshi.lettera.Lettera.UTF8;
import io.francitoshi.lettera.data.PlainNote;
import io.francitoshi.lettera.data.Friend;
import io.francitoshi.lettera.data.Sender;
import io.nut.base.crypto.gpg.GPG;
import io.nut.base.security.SecureChars;
import io.nut.base.concurrent.actor.ActorHub;
import io.nut.core.net.mail.SMTP;
import jakarta.mail.MessagingException;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Consumer;

public class MailPush implements Consumer<PlainNote>
{
    private static final GPG GPG = new GPG();

    private volatile boolean active;
    private final Sender sender;
    private final Friend friend;
    private final KeyWrapper keyWrapper;

    private final SecureChars secureEmailPass;
    private final SMTP smtp;
    private final ActorHub hub;
    private final java.util.function.Consumer<PlainNote> publisher;

    public MailPush(Sender sender, Friend friend, KeyWrapper keyWrapper, SecureChars secureEmailPass, ActorHub hub)
    {
        this.sender = sender;
        this.friend = friend;
        this.keyWrapper = keyWrapper;
        this.secureEmailPass = secureEmailPass;
        this.smtp = new SMTP(sender.smtpHost, sender.smtpPort, sender.auth, sender.starttls, sender.username, secureEmailPass, sender.email);
        this.hub = hub;
        this.publisher = hub.pub("PlainNote");
    }

    public boolean sendNote(String text) throws MessagingException, InterruptedException, IOException
    {
        byte[] plainBytes = text.getBytes(UTF8);
        char[] gpgPass = keyWrapper.unwrapKey("gpg", "pass", sender.gpgPassphrase);
        byte[] encryptedText = GPG.encryptAndSign(plainBytes, sender.email, gpgPass, friend.email);
        Arrays.fill(gpgPass, '\0');
        if (!smtp.isConnected())
        {
            smtp.connect();
        }
        String subject = "lettera " + sender.gpgKeyId + "-" + friend.fingerprint;
        smtp.send(subject, new String(encryptedText, UTF8), friend.email);
        return true;
    }

    public void close()
    {
        this.active = false;
        if(smtp.isConnected())
        {
            smtp.close();
        }
    }

    @Override
    public void accept(PlainNote note)
    {
        boolean rc;
        try
        {
            rc = sendNote(note.text);
        }
        catch (MessagingException ex)
        {
            System.getLogger(MailPush.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        catch (InterruptedException ex)
        {
            System.getLogger(MailPush.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        catch (IOException ex)
        {
            System.getLogger(MailPush.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
//        if(rc)
        {
            //666 note.setSent(JavaTime.epochSecond());
//666                publisher.accept(note);
        }
    }
}
