/*
 * Copyright (C) 2025-2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.lettera;

import io.francitoshi.lettera.email.MailPush;
import io.francitoshi.lettera.email.MailPoll;
import io.francitoshi.lettera.data.Friend;
import io.francitoshi.lettera.data.Sender;
import de.mkammerer.argon2.Argon2Advanced;
import de.mkammerer.argon2.Argon2Factory;
import io.francitoshi.lettera.bot.HubBot;
import io.francitoshi.lettera.data.PlainNote;
import io.nut.base.crypto.KeyStoreManager;
import io.nut.base.crypto.Kripto;
import io.nut.base.crypto.Passphraser;
import io.nut.base.crypto.Rand;
import io.nut.base.crypto.SecureWrapper;
import io.nut.base.crypto.gpg.GPG;
import io.nut.base.crypto.gpg.MainKey;
import io.nut.base.crypto.gpg.PubKey;
import io.nut.base.crypto.gpg.SecKey;
import io.nut.base.crypto.gpg.UserId;
import io.nut.base.encoding.Ascii85;
import io.nut.base.encoding.Base64DecoderException;
import io.nut.base.logging.Log;
import io.nut.base.net.Emails;
import io.nut.base.security.SecureChars;
import io.nut.base.text.Table;
import io.nut.base.concurrent.Lazy;
import io.nut.base.concurrent.actor.ActorHub;
import jakarta.mail.MessagingException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jline.utils.AttributedString;

public class Lettera implements AutoCloseable
{
    public static final Charset UTF8 = StandardCharsets.UTF_8;
    public static final int LOOP_MILLIS = 15_000;
    public static final String HR = "----------------------------------------";
    
    public static final int TRUST_LEVEL_KEY_MISMATCH_DANGER = -1;
    public static final int TRUST_LEVEL_UNKNOWN = 0;
    public static final int TRUST_LEVEL_UNVERIFIED = 1;
    public static final int TRUST_LEVEL_VERIFIED_MANUAL = 2;
    public static final int TRUST_LEVEL_VERIFIED_JPAKE = 3;
    
    static final Kripto KRIPTO = Kripto.getInstance(false);
    static final Rand RAND = Kripto.getRand();
    static final GPG GPG = new GPG().setArmor(true);
    
    static final String DB = "db";
    
    static final int KEY_BYTES = 32;
    static final int SALT_BYTES = 32;
    static final Argon2Advanced ARGON2 = Argon2Factory.createAdvanced(Argon2Factory.Argon2Types.ARGON2id, SALT_BYTES, KEY_BYTES);
    
    static final String GPG_PASSPHRASE_WRAP = "gpg+passphrase";
    static final Log LOG = Log.of(Lettera.class);
    
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
    final File letteraDir;
    private volatile String username;
    private volatile SecureChars passphrase;
    private volatile File configFile;
    private volatile File keystoreFile;
    private volatile File letteraDb;
    final boolean mock;
    final boolean debug;
    final boolean console;
    final boolean passpath;
    
    volatile KeyWrapper keyWrapper;
    volatile Passphraser passphraser;
    volatile KeyStoreManager ksm;
    
    volatile LetteraDb db;

    public static final int TTL = 4321;
    
    final ActorHub hub;
    
    final Supplier<SecKey[]> secs = new Lazy<>(TTL, ()->
    {
        try
        {
            return GPG.getSecKeys();
        }
        catch (IOException | InterruptedException ex)
        {
            throw new RuntimeException(ex);
        }
    });
    final Supplier<PubKey[]> pubs = new Lazy<>(TTL, ()->
    {
        try
        {
            return GPG.getPubKeys();
        }
        catch (IOException | InterruptedException ex)
        {
            throw new RuntimeException(ex);
        }
    });
    
    volatile Sender sender;
    private volatile Friend currentSession;
    private volatile Map<Long, PlainNote> currentNotes;

    private volatile Mode mode = Mode.ReadWrite;
    private volatile MailPush mailPush;
    private volatile MailPoll mailPoll;
    volatile HubBot hubBot;

    
    public Lettera(ActorHub hub, PrintStream out, File letteraDir, String username, SecureChars passphrase, boolean passpath, boolean mock, boolean debug)
    {
        this.hub = hub;
        this.out = out;
        this.letteraDir = letteraDir;
        this.username = username;
        this.passphrase = passphrase;
        this.passpath = passpath;
        this.mock = mock;
        this.debug = debug;
        this.console = System.console()!=null;
    }
    
    public Lettera open() throws IOException, Base64DecoderException, InterruptedException, KeyStoreException, NoSuchAlgorithmException, CertificateException, Exception
    {
        letteraDir.mkdirs();
        if(username==null)
        {
            username = PassphraseManager.getUsername(mock);
        }

        this.configFile = new File(letteraDir, username+".properties");
        this.keystoreFile = new File(letteraDir, username+".p12");
        this.letteraDb = new File(letteraDir, username+".db");
        
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
            if(passphrase==null)
            {
                return this;
            }
        }
            
        long t0 = System.nanoTime();
        final Config finalConfig = config;
        byte[] seed = passphrase.apply((pass)-> ARGON2.rawHash(finalConfig.iterations, finalConfig.memoryKB, finalConfig.parallelism, pass, finalConfig.getSalt()));        

        long t1 = System.nanoTime();
        
        LOG.debug("argon2 = %d ms", TimeUnit.NANOSECONDS.toMillis(t1-t0));
        
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
        
        //666 this.hubBot = new HubBot(hive, HR, db, dbPass);
        
        return this;
    }
    
    public String startChat(String session, Mode mode)
    {
        currentSession = db.getFriend(session);
        
//666        Chat chat2 = Chat.build(sender, currentSession, currentSession.mutualAuthProof);

//666        if(!chat.equals(chat2))
        {
            System.err.println("WARNING: FIELDS CHANGED");
//666            System.err.println(chat.diff(chat2));
        }
        
//666        currentChat = chat;
//666        currentNotes = db.getNotes(session);
        
        SecureChars secureEmailPass = new SecureChars(keyWrapper.unwrapKey("email", "pass", sender.emailPass));
        SecureChars secureGpgPass = new SecureChars(keyWrapper.unwrapKey("gpg", "pass", sender.gpgPassphrase));
        
//666        mailReader = new IMAP(currentAccount.imapHost, currentAccount.imapPort, currentAccount.auth, currentAccount.starttls, false, currentAccount.username, secureEmailPass);
//666        smtp = new SMTP(currentAccount.smtpHost, currentAccount.smtpPort, currentAccount.auth, currentAccount.starttls, currentAccount.username, secureEmailPass, currentAccount.address);

//666        this.mailPoll = mode.read ? new MailPoll(currentChat, sender, currentSession, keyWrapper, secureEmailPass, this).start() : null;
//666        this.mailPush = mode.write? new MailPush(currentChat, sender, currentSession, keyWrapper, secureEmailPass, this) : null;

//666        return chat.id;
        return null;
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
        
    public int countFriends()
    {
//666        return db.getFriends().length;
        return 666;
    }

    public int countSecKeys() throws IOException, InterruptedException
    {
        return secs.get().length;
    }

    public int countPubKeys() throws IOException, InterruptedException
    {
        return pubs.get().length;
    }
    
    public int listSender()
    {
        sender=db.getSender();
        this.out.println(HR);
        if(sender!=null)
        {
            this.out.printf("sender: %s - %s\n",sender.email,sender.gpgKeyId);
        }
        return sender!=null ? 1 : 0;
    }
    public int listFriends()
    {
        Friend[] items = db.getFriends();
        Table table = new Table(items.length, 3, false);
        for(int r=0;r<items.length;r++)
        {
            table.setCell(r,0, items[r].name);
            table.setCell(r,1, items[r].email);
            table.setCell(r,2, items[r].fingerprint);
        }
        this.out.println(HR);
        this.out.println("Friends: "+items.length);
        this.out.println(table.toString());
        return items.length;
    }
        
    public Set<String> getFriendNames()
    {
        Friend[] items = db.getFriends();
        HashSet<String> set = new HashSet<>();
        for (Friend item : items)
        {
            set.add(item.name);
        }
        return set;
    }
    
    public Set<String> getPubKeysAddresses(PubKey[] pubs)
    {
        HashSet<String> set = new HashSet<>();
        for (PubKey item : pubs)
        {
            for(UserId uid : item.getUids())
            {
                String[] nameEmail = Emails.parseEmailAddress(uid.uid);
                set.add(nameEmail[1]);
            }
        }
        return set;
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

    static final Pattern EMAIL_PATTERN1 = Pattern.compile(".*<(.+@.+)>.*");
    static final Pattern EMAIL_PATTERN2 = Pattern.compile("([^<>]+@[^<>]+)");

    static String getEmail(String uid)
    {
        Matcher m1 = EMAIL_PATTERN1.matcher(uid);
        if(m1.matches())
        {
            return m1.group(1);
        }
        Matcher m2 = EMAIL_PATTERN2.matcher(uid);
        if(m2.matches())
        {
            return m2.group(1);
        }
        return null;
    }
    
    static Set<String> getEmails(MainKey[] keys)
    {
        Set<String> set = new HashSet<>();
        for(MainKey item : keys)
        {
            for(UserId uid : item.getUids())
            {
                String email = getEmail(uid.uid);
                if(email!=null)
                {
                    set.add(email);
                }
            }
        }
        return set;
    }
    
    static Set<String> getFingerprints(String address, MainKey[] keys)
    {
        HashSet<String> set = new HashSet<>();
        for(MainKey item : keys)
        {
            String fp = item.getMain().getFingerprint();
            for(UserId uid : item.getUids())
            {
                String email = getEmail(uid.uid);
                if(address.equalsIgnoreCase(email))
                {
                    set.add(fp);
                }
            }
        }
        return set;
    }
    
}
