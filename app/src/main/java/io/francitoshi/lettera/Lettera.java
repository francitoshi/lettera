/*
 *  Lettera.java
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

import de.mkammerer.argon2.Argon2Advanced;
import de.mkammerer.argon2.Argon2Factory;
import static io.francitoshi.lettera.TerminalChat.DB;
import io.nut.base.crypto.KeyStoreManager;
import io.nut.base.crypto.Kripto;
import io.nut.base.crypto.Passphraser;
import io.nut.base.crypto.Rand;
import io.nut.base.crypto.SecureWrapper;
import io.nut.base.crypto.gpg.GPG;
import io.nut.base.crypto.gpg.PubKey;
import io.nut.base.crypto.gpg.SecKey;
import io.nut.base.encoding.Ascii85;
import io.nut.base.encoding.Base64DecoderException;
import io.nut.base.security.SecureChars;
import io.nut.base.text.Table;
import io.nut.base.util.concurrent.hive.Bee;
import io.nut.base.util.concurrent.hive.Hive;
import jakarta.mail.MessagingException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jline.utils.AttributedString;

public class Lettera extends Bee<Note> implements AutoCloseable
{
    public static final String GPG_PURPOSE = "gpg";
    public static final Charset UTF8 = StandardCharsets.UTF_8;
    public static final int LOOP_MILLIS = 15_000;
    public static final String HR = "----------------------------------------";
    
    static final Kripto KRIPTO = Kripto.getInstance(false);
    static final Rand RAND = Kripto.getRand();
    static final GPG GPG = new GPG().setArmor(true);
    
    static final String DB = "db";
    
    static final int KEY_BYTES = 32;
    static final int SALT_BYTES = 32;
    static final Argon2Advanced ARGON2 = Argon2Factory.createAdvanced(Argon2Factory.Argon2Types.ARGON2id, SALT_BYTES, KEY_BYTES);
    
    public enum Mode 
    {
        Read(true, false), Write(false, true), ReadWrite(true, true);
        private Mode(boolean read, boolean write)
        {
            this.read = read;
            this.write = write;
        }
        public final boolean read;
        public final boolean write;
    };
    
    final PrintStream out;
    final File configFile;
    final File keystoreFile;
    final File letteraDb;
    volatile SecureChars passphrase;
    final boolean mock;
    final boolean debug;
    final boolean console;
    
    volatile KeyWrapper keyWrapper;
    volatile Passphraser passphraser;
    volatile KeyStoreManager ksm;
    
    volatile LetteraDb db;

    volatile SecKey[] secs;
    volatile PubKey[] pubs;
    
    private volatile Account currentAccount;
    private volatile Friend currentFriend;
    volatile Chat currentChat;
    private volatile Map<Long, Note> currentNotes;

    private volatile Mode mode = Mode.ReadWrite;
    private volatile MailPush mailPush;
    private volatile MailPoll mailPoll;
    volatile SessionHub sessionHub;

    final Hive hive = new Hive(Hive.CORES, Hive.CORES, Hive.CORES, 30_000);
    
    public Lettera(PrintStream out, File configFile, File keystoreFile, File letteraDb, SecureChars passphrase, boolean mock, boolean debug)
    {
        this.out = out;
        this.configFile = configFile;
        this.keystoreFile = keystoreFile;
        this.letteraDb = letteraDb;
        this.passphrase = passphrase;
        this.mock = mock;
        this.debug = debug;
        this.console = System.console()!=null;
    }
    
    public Lettera open() throws IOException, Base64DecoderException, InterruptedException, KeyStoreException, NoSuchAlgorithmException, CertificateException, Exception
    {
        boolean firstTime = !configFile.exists() || !keystoreFile.exists();
        Config config = Config.load(configFile);
        if(config==null)
        {
            config = Config.createDefault(configFile);
            firstTime = true;
        }
        
        if(passphrase==null)
        {
            passphrase = new SecureChars(firstTime ? PassphraseManager.createPassphrase(mock) : PassphraseManager.getPassphrase(mock));
        }
        long t0 = System.nanoTime();
        final Config finalConfig = config;
        byte[] seed = passphrase.apply((pass)-> ARGON2.rawHash(finalConfig.iterations, finalConfig.memoryKB, finalConfig.parallelism, pass, finalConfig.getSalt()));        

        long t1 = System.nanoTime();
        if(debug)
        {
            this.out.printf("argon2 = %d ms\n", TimeUnit.NANOSECONDS.toMillis(t1-t0));
        }
        
        passphraser = KRIPTO.getPassphraserHkdf(KRIPTO.getHkdfWithSha512(), seed, config.getSalt());
        ksm = KRIPTO.getKeyStoreManagerPKCS12(passphraser);
        keyWrapper = new KeyWrapper(new SecureWrapper(KRIPTO, seed, Kripto.Hkdf.HkdfWithSha512));
        
        if(keystoreFile.exists())
        {        
            ksm.load(keystoreFile);
        }
        else
        {
            firstTime = true;
        }

        char[] dbPass;        
        if(firstTime || (dbPass=ksm.getPassphrase(DB))==null)
        {
            dbPass = Ascii85.encode(RAND.nextBytes(new byte[32]));
            ksm.setPassphrase(DB, dbPass);
            firstTime = true;
        }
        
        if(ksm.isModified())
        {
            ksm.store(keystoreFile);
        }
        
        this.db = new LetteraDb(this.letteraDb, dbPass);
        
        loadAllKeys();
        return this;
    }
    
    public String startChat(String session, Mode mode)
    {
        Chat chat = db.getChat(session);
        if(chat==null)
        {
            return null;
        }
//666        verificar que no han cambiado las direcciones ni las keyid
                
        currentAccount = db.getAccount(chat.accountName);
        currentFriend = db.getFriend(chat.friendName);
        
        Chat chat2 = Chat.build(currentAccount, currentFriend, chat.mutualAuthProof);

        if(!chat.equals(chat2))
        {
            System.err.println("WARNING: FIELDS CHANGED");
            System.err.println(chat.diff(chat2));
        }
        
        currentChat = chat;
        currentNotes = db.getNotes(session);
        
        SecureChars secureEmailPass = new SecureChars(keyWrapper.unwrapKey("email", currentAccount.name, currentAccount.emailPass));
        SecureChars secureGpgPass = new SecureChars(keyWrapper.unwrapKey(GPG_PURPOSE, currentAccount.name, currentAccount.gpgPass));
        
//666        mailReader = new IMAP(currentAccount.imapHost, currentAccount.imapPort, currentAccount.auth, currentAccount.starttls, false, currentAccount.username, secureEmailPass);
//666        smtp = new SMTP(currentAccount.smtpHost, currentAccount.smtpPort, currentAccount.auth, currentAccount.starttls, currentAccount.username, secureEmailPass, currentAccount.address);

        this.mailPoll = mode.read ? new MailPoll(currentChat, currentAccount, currentFriend, keyWrapper, secureEmailPass, this).start() : null;
        this.mailPush = mode.write? new MailPush(currentChat, currentAccount, currentFriend, keyWrapper, secureEmailPass, this) : null;

        return chat.accountName;
    }
    
    public void send(String text) throws MessagingException, InterruptedException, IOException
    {
        this.mailPush.sendNote(text);
    }
        
    public static String stripAnsi(String input)
    {
        if (input == null || input.isEmpty())
        {
            return input;
        }
        // AttributedString puede parsear una cadena con códigos ANSI.
        // El método .toAnsi() la reconstruye, pero el método .plain() la devuelve como texto plano.
        return AttributedString.stripAnsi(input);
    }    
    

    void loadAllKeys() throws IOException, InterruptedException
    {
        secs = GPG.getSecKeys();
        pubs = GPG.getPubKeys();
    }
    
    public int countAccounts()
    {
        return db.getAccounts().length;
    }
    public int countFriends()
    {
        return db.getFriends().length;
    }

    public int countSecKeys() throws IOException, InterruptedException
    {
        return GPG.getSecKeys().length;
    }
    public int countPubKeys() throws IOException, InterruptedException
    {
        return GPG.getPubKeys().length;
    }
    
    public int listAccounts()
    {
        Account[] items = db.getAccounts();
        Table table = new Table(items.length, 3, false);
        for(int r=0;r<items.length;r++)
        {
            table.setCell(r,0, items[r].name);
            table.setCell(r,1, items[r].address);
            table.setCell(r,2, items[r].keyid);
        }
        this.out.println(HR);
        this.out.println("Accounts: "+items.length);
        this.out.println(table.toString());
        return items.length;
    }

    public int listFriends()
    {
        Friend[] items = db.getFriends();
        Table table = new Table(items.length, 3, false);
        for(int r=0;r<items.length;r++)
        {
            table.setCell(r,0, items[r].name);
            table.setCell(r,1, items[r].address);
            table.setCell(r,2, items[r].keyid);
        }
        this.out.println(HR);
        this.out.println("Friends: "+items.length);
        this.out.println(table.toString());
        return items.length;
    }

    public int listChats()
    {
        Chat[] items = db.getChats();
        Table table = new Table(items.length, 3, false);
        for(int r=0;r<items.length;r++)
        {
            table.setCell(r,0, items[r].id);
            table.setCell(r,1, items[r].accountAddress);
            table.setCell(r,2, items[r].friendAddress);
        }
        this.out.println(HR);
        this.out.println("Chats: "+items.length);
        this.out.println(table.toString());
        return items.length;
    }

    @Override
    public void close() throws Exception
    {
        if(mailPoll!=null)
        {
            mailPoll.close();
            mailPoll = null;
        }
        if(mailPush!=null)
        {
            mailPush.close();
            mailPush = null;
        }
        db.close();
    }

    @Override
    protected void receive(Note m)
    {
        //do nothing
    }
}
