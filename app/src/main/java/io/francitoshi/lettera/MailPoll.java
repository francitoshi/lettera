/*
 *  MailGetBot.java
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
package io.francitoshi.lettera;

import static io.francitoshi.lettera.Lettera.GPG_PURPOSE;
import static io.francitoshi.lettera.Lettera.LOOP_MILLIS;
import io.nut.base.crypto.gpg.GPG;
import io.nut.base.security.SecureChars;
import io.nut.base.util.Utils;
import io.nut.base.util.concurrent.hive.Bee;
import io.nut.core.net.mail.IMAP;
import io.nut.core.net.mail.MailReader;
import io.nut.core.net.mail.SMTP;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import java.awt.Toolkit;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MailPoll implements Runnable
{
    private static final GPG GPG = Lettera.GPG;
    
    private volatile boolean active;
    private final Chat currentChat;
    private final Account currentAccount;
    private final Friend currentFriend;
    private final KeyWrapper keyWrapper;
//666    private final Map<Long, Note> currentNotes;
    private final SecureChars secureEmailPass;
    
    private final Bee<Note> hub;
    private final Object lock = new Object();
    private volatile int waitMillis = 0;

    public MailPoll(Chat currentChat, Account currentAccount, Friend currentFriend, KeyWrapper keyWrapper, SecureChars secureEmailPass, Bee<Note> hub)
    {
        this.currentChat = currentChat;
        this.currentAccount = currentAccount;
        this.currentFriend = currentFriend;
        this.keyWrapper = keyWrapper;
        this.secureEmailPass = secureEmailPass;
        this.hub = hub;
    }    
    
    @Override
    public void run()
    {
        active=true;
        Date after=null;
        final MailReader mailReader = new IMAP(currentAccount.imapHost, currentAccount.imapPort, currentAccount.auth, currentAccount.starttls, false, currentAccount.username, secureEmailPass);
        while(active)
        {
            waitMillis += LOOP_MILLIS;
            try
            {
                Note note;
                if (!mailReader.isConnected())
                {
                    mailReader.connect();
                }
                Message[] messages = after != null ? mailReader.getMessages(after) : mailReader.getMessages();
                for (Message item : messages)
                {
                    after = Utils.max(after != null ? after : new Date(0), item.getReceivedDate());
                    if (isCurrentChatSession(item))
                    {
                        char[] gpgPass = keyWrapper.unwrapKey(GPG_PURPOSE, currentChat.accountName, currentAccount.gpgPass);
                        //666 hub.send(item, gpgPass);
                        //666 hub.send(note);
                        Arrays.fill(gpgPass, '\0');
                        Toolkit.getDefaultToolkit().beep();
                    }
                }
                synchronized (lock)
                {
                    lock.wait(waitMillis);
                }
            }
            catch (InterruptedException | IOException | MessagingException ex)
            {
                System.getLogger(TerminalChat.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            }
            catch (Exception ex)
            {
                System.getLogger(TerminalChat.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            }
        }
    }

    private boolean isCurrentChatSession(Message item) throws MessagingException
    {
        for(Address from : item.getFrom())
        {
            if(from.toString().contains(currentChat.accountAddress))
            {
                for(Address to : item.getAllRecipients())
                {
                    if(to.toString().contains(currentChat.friendAddress))
                    {
                        return true;
                    }
                }
                return false;
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
