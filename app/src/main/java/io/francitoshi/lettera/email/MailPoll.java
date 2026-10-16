/*
 * Copyright (C) 2025-2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.lettera.email;

import io.francitoshi.lettera.KeyWrapper;
import io.francitoshi.lettera.data.PlainNote;
import io.francitoshi.lettera.data.Friend;
import io.francitoshi.lettera.data.Sender;
import io.nut.base.commons.concurrent.actor.ActorHub;
import io.nut.base.commons.security.SecureChars;
import io.nut.base.crypto.gpg.GPG;
import io.nut.base.util.Utils;
import io.nut.core.net.mail.IMAP;
import io.nut.core.net.mail.MailReader;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import java.awt.Toolkit;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;

public class MailPoll implements Runnable
{
    private static final GPG GPG = new GPG();
    private static final int LOOP_MILLIS = 60_000;
    
    private volatile boolean active;
    private final Sender sender;
    private final Friend friend;
    private final KeyWrapper keyWrapper;
//666    private final Map<Long, Note> currentNotes;
    private final SecureChars secureEmailPass;
    
    private final ActorHub hub;
    private final java.util.function.Consumer<PlainNote> publisher;
    private final Object lock = new Object();
    private volatile int waitMillis = 0;

    public MailPoll(Sender currentAccount, Friend currentFriend, KeyWrapper keyWrapper, SecureChars secureEmailPass, ActorHub hub)
    {
        this.sender = currentAccount;
        this.friend = currentFriend;
        this.keyWrapper = keyWrapper;
        this.secureEmailPass = secureEmailPass;
        this.hub = hub;
        this.publisher = hub.pub("PlainNote");
    }    
    
    @Override
    public void run()
    {
        active=true;
        Date after=null;
        boolean imapSsl = sender.imapPort == IMAP.SAFE_PORT_993;
        final MailReader mailReader = new IMAP(sender.imapHost, sender.imapPort, sender.auth, imapSsl, !imapSsl, false, sender.username, secureEmailPass);
        while(active)
        {
            waitMillis += LOOP_MILLIS;
            try
            {
                PlainNote note;
                if (!mailReader.isConnected())
                {
                    mailReader.connect();
                }
                Message[] messages = after != null ? mailReader.getMessages(after) : mailReader.getMessages();
                for (Message item : messages)
                {
                    after = Utils.max(after != null ? after : new Date(0), item.getReceivedDate());
                    if(isCurrentChatSession(item))
                    {
                        char[] gpgPass = keyWrapper.unwrapKey("gpg", "pass", sender.gpgPassphrase);
                        try
                        {
                            //666 hub.send(item, gpgPass);
                            //666 hub.send(note);
                            Toolkit.getDefaultToolkit().beep();
                        }
                        finally
                        {
                            Arrays.fill(gpgPass, '\0');
                        }
                    }
                }
                synchronized (lock)
                {
                    lock.wait(waitMillis);
                }
            }
            catch (InterruptedException | IOException | MessagingException ex)
            {
                System.getLogger(MailPoll.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            }
            catch (Exception ex)
            {
                System.getLogger(MailPoll.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            }
        }
    }

    private boolean isCurrentChatSession(Message item) throws MessagingException
    {
        Address[] from = item.getFrom();
        if (from == null || !containsEmail(from, friend.email))
        {
            return false;
        }
        Address[] to = item.getAllRecipients();
        return to != null && containsEmail(to, sender.email);
    }

    private static boolean containsEmail(Address[] addresses, String email)
    {
        if (email == null || email.isEmpty())
        {
            return false;
        }
        for (Address address : addresses)
        {
            if (address instanceof InternetAddress)
            {
                if (email.equalsIgnoreCase(((InternetAddress) address).getAddress()))
                {
                    return true;
                }
            }
            else if (address != null && email.equalsIgnoreCase(address.toString()))
            {
                return true;
            }
        }
        return false;
    }
    public MailPoll start()
    {
        Thread th = new Thread(this, "MailGetBot");
        th.setDaemon(true);
        th.start();
        return this;
    }
    
    public void close()
    {
        this.active=false;
    }
    
    public void sync()
    {
        synchronized (lock)
        {
            waitMillis=0;
            lock.notifyAll();
        }
    }
}
