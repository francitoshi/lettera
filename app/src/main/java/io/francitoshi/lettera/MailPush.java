/*
 *  MailPush.java
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
import io.nut.base.time.JavaTime;
import io.nut.base.util.Utils;
import io.nut.base.util.concurrent.hive.Bee;
import io.nut.core.net.mail.SMTP;
import jakarta.mail.MessagingException;
import java.io.IOException;
import java.util.Arrays;

public class MailPush extends Bee<Note>
{
    private static final GPG GPG = Lettera.GPG;

    private volatile boolean active;
    private final Chat currentChat;
    private final Account currentAccount;
    private final Friend currentFriend;
    private final KeyWrapper keyWrapper;

    private final SecureChars secureEmailPass;
    private final SMTP smtp;
    private final Bee<Note> hub;

    public MailPush(Chat currentChat, Account currentAccount, Friend currentFriend, KeyWrapper keyWrapper, SecureChars secureEmailPass, Bee<Note> hub)
    {
        this.currentChat = currentChat;
        this.currentAccount = currentAccount;
        this.currentFriend = currentFriend;
        this.keyWrapper = keyWrapper;
        this.secureEmailPass = secureEmailPass;
        this.smtp = new SMTP(currentAccount.smtpHost, currentAccount.smtpPort, currentAccount.auth, currentAccount.starttls, currentAccount.username, secureEmailPass, currentAccount.address);
        this.hub = hub;
    }

    public boolean sendNote(String text) throws MessagingException, InterruptedException, IOException
    {
        byte[] plainBytes = text.getBytes(UTF8);
        char[] gpgPass = keyWrapper.unwrapKey(GPG_PURPOSE, currentChat.accountName, currentAccount.gpgPass);
        byte[] encryptedText = GPG.encryptAndSign(plainBytes, currentChat.accountAddress, gpgPass, currentChat.friendAddress);
        Arrays.fill(gpgPass, '\0');
        if (!smtp.isConnected())
        {
            smtp.connect();
        }
        String subject = "lettera " + currentChat.accountKeyid + "-" + currentChat.friendKeyid+Utils.firstNonNull(currentChat.mutualAuthProof,"");
        smtp.send(subject, new String(encryptedText, UTF8), currentChat.friendAddress);
        return true;
    }

    @Override
    protected void receive(Note note)
    {
        try
        {
            boolean rc = sendNote(note.text);
            if(rc)
            {
                note.setSent(JavaTime.epochSecond());
                hub.send(note);
            }
        }
        catch (MessagingException | InterruptedException | IOException ex)
        {
            System.getLogger(MailPush.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
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
