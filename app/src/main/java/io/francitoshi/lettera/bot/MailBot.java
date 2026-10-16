/*
 * Copyright (C) 2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.lettera.bot;

import io.francitoshi.lettera.data.SealedNote;
import io.francitoshi.lettera.email.ServerSettings;
import io.nut.base.commons.concurrent.actor.ActorHub;
import io.nut.base.commons.security.SecureChars;
import io.nut.base.commons.time.JavaTime;
import io.nut.core.net.mail.IMAP;
import io.nut.core.net.mail.SMTP;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MailBot
{
    private static final int MAX_BODY_BYTES = 4*1024;

    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicBoolean active = new AtomicBoolean();

    private final ActorHub actorHub;
    private final SMTP smtp;
    private final IMAP imap;
    
    public MailBot(ActorHub actorHub, ServerSettings smtp, ServerSettings imap, String username, SecureChars pass, String from)
    {
        this.actorHub = actorHub;
        this.smtp = new SMTP(smtp.getHost(), smtp.getPort(), true, smtp.isTls(), username, pass, from);
        this.imap = new IMAP(imap.getHost(), imap.getPort(), true, imap.isTls(), false, username, pass);
        actorHub.sub(from, this::onSealedNote);
    }   

    protected void onSealedNote(SealedNote note)
    {
        try
        {
            smtp.reconnect(30_000);
            String subject = "lettera "+ZonedDateTime.now().format(JavaTime.YYYY_MM_DD_HH_MM_SSz);
            smtp.send(subject, note.text, note.outerEmail); 
            if(smtp.isConnected())
            {
                smtp.close();
            }
        }
        catch (MessagingException ex)
        {
            Logger.getLogger(MailBot.class.getName()).log(Level.SEVERE, (String) null, ex);
        }
    }
    
    private final IMAP.ImapListener inputMessageBee = new IMAP.ImapListener()
    {
        @Override
        public void send(Message msg, long uidValidity, long lastUID)
        {
            try
            {
                long epochSecond = JavaTime.epochSecond(msg.getSentDate());
                String from = Arrays.toString(msg.getFrom());
                String to = Arrays.toString(msg.getAllRecipients());
                String body;
                try(InputStream in = msg.getInputStream())
                {
                    body = new String(in.readNBytes(MAX_BODY_BYTES), StandardCharsets.UTF_8);
                }
                SealedNote note = null;//new SealedNote(to, from, );
//666                hub.send(note);
            }
            catch (MessagingException | IOException  ex)
            {
                Logger.getLogger(MailBot.class.getName()).log(Level.SEVERE, (String) null, ex);
            }
        }
    };
    
    public void open() throws Exception
    {
        if(inputMessageBee!=null)
        {
            imap.setImapListener(inputMessageBee, 0, 0);
        }
        imap.connect();
//666        new Thread(this::run,ImapBee.class.getName()).start();
    }

    
    public void run()
    {
        active.set(true);
        try
        {
            while(active.get())
            {
                imap.idle();
            }
        }
        catch (MessagingException ex)
        {
//666            Logger.getLogger(ImapBee.class.getName()).log(Level.SEVERE, (String) null, ex);
        }
        finally
        {
            active.set(false);
        }
    }
        
    public void close()
    {
        active.set(false);
        smtp.close();
        imap.close();
    }

}
