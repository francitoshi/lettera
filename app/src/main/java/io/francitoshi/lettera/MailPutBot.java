/*
 *  MailPutBot.java
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
import static io.francitoshi.lettera.Lettera.UTF8;
import io.nut.base.crypto.gpg.GPG;
import io.nut.base.security.SecureChars;
import io.nut.base.util.Utils;
import io.nut.base.util.concurrent.hive.Bee;
import io.nut.core.net.mail.SMTP;
import jakarta.mail.MessagingException;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MailPutBot extends Bee<Note> implements Runnable
{

    private static final GPG GPG = Lettera.GPG;

    private volatile boolean active;
    private final Chat currentChat;
    private final Account currentAccount;
    private final Friend currentFriend;
    private final KeyWrapper keyWrapper;
    
//666    private final Map<Long, Note> currentNotes;
    private final SecureChars secureEmailPass;
    private final SMTP smtp;
    private final BlockingQueue<Note> queue = new LinkedBlockingQueue<>(8);

    public MailPutBot(Chat currentChat, Account currentAccount, Friend currentFriend, KeyWrapper keyWrapper, SecureChars secureEmailPass)
    {
        this.currentChat = currentChat;
        this.currentAccount = currentAccount;
        this.currentFriend = currentFriend;
        this.keyWrapper = keyWrapper;
        this.secureEmailPass = secureEmailPass;
        this.smtp = new SMTP(currentAccount.smtpHost, currentAccount.smtpPort, currentAccount.auth, currentAccount.starttls, currentAccount.username, secureEmailPass, currentAccount.address);
    }

    public void sendNote(String text) throws MessagingException, InterruptedException, IOException
    {
        byte[] plainBytes = text.getBytes(UTF8);
        char[] gpgPass = keyWrapper.unwrapKey(GPG_PURPOSE, currentChat.accountName, currentAccount.gpgPass);
        byte[] encryptedText = GPG.encryptAndSign(plainBytes, currentChat.accountAddress, gpgPass, currentChat.friendAddress);
        Arrays.fill(gpgPass, '\0');
//666                        currentNotes.put(JavaTime.epochSecond(), note);
        if (!smtp.isConnected())
        {
            smtp.connect();
        }
        String subject = "lettera " + currentChat.accountKeyid + "-" + currentChat.friendKeyid+Utils.firstNonNull(currentChat.mutualAuthProof,"");
        smtp.send(subject, new String(encryptedText, UTF8), currentChat.friendAddress);
    }

    @Override
    public void run()
    {
        Note note;
        active = true;
        try
        {
            while ((note = queue.poll()) != null && active)
            {
                sendNote(note.text);
            }
        }
        catch (InterruptedException | IOException | MessagingException ex)
        {
            System.getLogger(MailPutBot.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
    }


    @Override
    protected void receive(Note note)
    {
        queue.add(note);
    }

    public MailPutBot start()
    {
        Thread th = new Thread(this, "MailPutBot");
        th.setDaemon(true);
        th.start();
        return this;
    }

    public void close()
    {
        this.active = false;
        if(smtp.isConnected())
        {
            smtp.close();
        }
    }
}
