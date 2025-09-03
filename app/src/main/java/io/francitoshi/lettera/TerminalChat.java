/*
 *  TerminalChat.java
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

import de.mkammerer.argon2.Argon2Advanced;
import de.mkammerer.argon2.Argon2Factory;
import io.nut.base.crypto.KeyStoreManager;
import io.nut.base.crypto.Kripto;
import io.nut.base.crypto.Passphraser;
import io.nut.base.crypto.Rand;
import io.nut.base.crypto.SecureWrapper;
import io.nut.base.crypto.gpg.GPG;
import io.nut.base.crypto.gpg.MainKey;
import io.nut.base.crypto.gpg.PASS;
import io.nut.base.crypto.gpg.PubKey;
import io.nut.base.crypto.gpg.SecKey;
import io.nut.base.crypto.gpg.SubKey;
import io.nut.base.crypto.gpg.UserId;
import io.nut.base.encoding.Ascii85;
import io.nut.base.encoding.Base64DecoderException;
import io.nut.base.io.IO;
import io.nut.base.io.console.AbstractConsole;
import io.nut.base.io.console.VirtualConsole;
import io.nut.base.security.SecureChars;
import io.nut.base.text.Table;
import io.nut.base.time.JavaTime;
import io.nut.base.util.Byter;
import io.nut.base.util.Chars;
import io.nut.base.util.Parsers;
import io.nut.base.util.Strings;
import io.nut.base.util.Utils;
import io.nut.core.net.mail.IMAP;
import io.nut.core.net.mail.MailReader;
import io.nut.core.net.mail.SMTP;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.StringJoiner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jline.builtins.Completers;
import static org.jline.builtins.Completers.TreeCompleter.node;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;

/**
 *
 * @author franci
 */
public class TerminalChat
{
    //https://patorjk.com/software/taag/#p=display&f=miniwi&t=lettera

    static final String VERSION = Utils.firstNonNull(Main.class.getPackage().getImplementationVersion(), "dev");
    static final String LETTERA;
    public static final String WELCOME;
    private static final String HELP;
    static 
    {
        ResourceBundle bundle = ResourceBundle.getBundle(TerminalChat.class.getName(), Locale.getDefault());
        LETTERA = getTextResource("lettera.txt", "LETTERA");
        String welcomeFileName = bundle.getString("welcome");
        WELCOME = getTextResource(welcomeFileName, "welcome").replace("$LETTERA$", LETTERA).replace("$VERSION$", VERSION);
        String helpFileName = bundle.getString("help");
        HELP = getTextResource(helpFileName, "help");
    }

    static final String DB = "db";
    
    private static String getTextResource(String fileName, String defaultValue)
    {
        try
        {
            return IO.readInputStreamAsString(TerminalChat.class.getResourceAsStream(fileName));
        }
        catch (IOException ex)
        {
            System.getLogger(TerminalChat.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        return defaultValue;        
    }

//    final Hive hive = new Hive(Hive.CORES, Hive.CORES, Hive.CORES, 30_000);
//    final Bee<Note> chatBee; 
//    final Bee<Integer> imapBee; 
//    final Bee<Note> smtpBee; 

    private static final Kripto KRIPTO = Kripto.getInstance(false);
    private static final Rand RAND = Kripto.getRand();
    private static final GPG GPG = new GPG().setArmor(true);
    
    private final Terminal terminal;
    private final File configFile;
    private final File keystoreFile;
    private final File letteraDb;
    private volatile LetteraDb db;
    private volatile SecureChars passphrase;
    private volatile SecureWrapper wrapper;
    private volatile Passphraser passphraser;
    private volatile KeyStoreManager ksm;
    private final boolean debug;
    
    private volatile LineReader reader;
    private volatile boolean console;
    private volatile Account currentAccount;
    private volatile Friend currentFriend;
    private volatile Chat currentChat;
    private volatile Map<Long, Note> currentNotes;
    private volatile MailReader mailReader;
    private volatile SMTP smtp;
    private volatile boolean wizard;
    
    private volatile SecKey[] secs;
    private volatile PubKey[] pubs;
        
    public TerminalChat(Terminal terminal, File configFile, File keystoreFile, File letteraDb, SecureChars passphrase, boolean debug)
    {
        this.terminal = terminal;
        this.configFile = configFile;
        this.keystoreFile = keystoreFile;
        this.letteraDb = letteraDb;
        this.passphrase = passphrase;
        this.debug = debug;
        this.console = System.console()!=null;
    }
    
    public void setWizard(boolean value)
    {
        this.wizard = value;
    }
    
    private static final String _HELP = "/help";
    private static final String _ABOUT = "/about";
    private static final String _SETUP_ACCOUNT = "/setup-account";
    private static final String _SETUP_FRIEND = "/setup-friend";
    private static final String _LIST_ACCOUNTS = "/list-accounts";
    private static final String _LIST_FRIENDS = "/list-friends";
    private static final String _LIST_CHATS = "/list-chats";
    private static final String _CHAT = "/chat";
    private static final String _UNREAD = "/unread";
    private static final String _WIZARD = "/wizard";
    private static final String _PASSPHRASE = "/passphrase";
    private static final String _EXIT = "/exit";

    private static final int KEY_BYTES = 32;
    private static final int SALT_BYTES = 32;
    private static final Argon2Advanced ARGON2 = Argon2Factory.createAdvanced(Argon2Factory.Argon2Types.ARGON2id, SALT_BYTES, KEY_BYTES);
    
    void run() throws IOException, InterruptedException, Base64DecoderException, NoSuchAlgorithmException, CertificateException, KeyStoreException, Exception
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
            passphrase = new SecureChars(firstTime ? PassphraseManager.createPassphrase(debug) : PassphraseManager.getPassphrase(debug));
        }
        long t0 = System.nanoTime();
        char[] pass = passphrase.getChars();
        byte[] seed = ARGON2.rawHash(config.iterations, config.memoryKB, config.parallelism, pass, config.getSalt());
        Arrays.fill(pass, '\0');

        long t1 = System.nanoTime();
        if(debug)
        {
            System.out.printf("argon2 = %d ms\n", TimeUnit.NANOSECONDS.toMillis(t1-t0));
        }
        
        passphraser = KRIPTO.getPassphraserHkdf(KRIPTO.hkdfWithSha512, seed, config.getSalt());
        ksm = KRIPTO.getKeyStoreManagerPKCS12(passphraser);
        wrapper = new SecureWrapper(KRIPTO, seed, Kripto.Hkdf.HkdfWithSha512);
        
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
        
        loadAllKeys();
        reader = buildLineReader(getCommandsCompleter());

        this.db = new LetteraDb(this.letteraDb, dbPass);
        try
        {
            ansiTitle("lettera");

            if(wizard && listAccounts()==0)
            {
                System.out.println("You need to create an account.");
                System.out.println("lettera>"+_SETUP_ACCOUNT);
                setupAccount();
            }
            if(wizard && listFriends()==0)
            {
                System.out.println("You need to create a friend.");
                System.out.println("lettera>"+_SETUP_FRIEND);
                setupFriend();
            }
            listChats();
            
            showHelp();

            showHelpTip();
            String prompt = "lettera";
            String line = reader.readLine();
            
            while ((line = reader.readLine(prompt+"> ")) != null)
            {
                line = line.trim();
                if (line.startsWith("/"))
                {
                    if (line.startsWith(_HELP))
                    {
                        showHelp();
                    }
                    else if (line.startsWith(_ABOUT))
                    {
                        System.out.println(TerminalChat.WELCOME);
                    }
                    else if (line.startsWith(_SETUP_ACCOUNT))
                    {
                        setupAccount();
                    }
                    else if (line.startsWith(_SETUP_FRIEND))
                    {
                        setupFriend();
                    }
                    else if (line.startsWith(_LIST_ACCOUNTS))
                    {
                        listAccounts();
                    }
                    else if (line.startsWith(_LIST_FRIENDS))
                    {
                        listFriends();
                    }
                    else if (line.startsWith(_LIST_CHATS))
                    {
                        listChats();
                    }
                    else if (line.startsWith(_CHAT))
                    {
                        String[] args = line.split(" ");
                        if(args.length>1)
                        {
                            startChat(args[1]);
                        }
                        else
                        {
                            startChat(setupChat());
                        }
                    }
                    else if (line.startsWith(_UNREAD))
                    {
                        //SendGmail.send("flikxxi@gmail.com", "Subject", "body");
                    }
                    else if (line.startsWith(_PASSPHRASE))
                    {
                        reader.printAbove("");
                        reader.printAbove("Not Yet Implemented!!!");
                        reader.printAbove("");
                        break;
                    }
                    else if (line.startsWith(_EXIT))
                    {
                        reader.printAbove("");
                        reader.printAbove("Bye!!!");
                        reader.printAbove("");
                        break;
                    }
                }
                else if(currentChat!=null)
                {
                    Note note = new Note(JavaTime.epochSecond(), currentChat.id, currentChat.accountAddress, currentChat.friendAddress, currentChat.accountKeyid, currentChat.friendKeyid, 0, 0, line);
                    chatQueue.add(note);
                    synchronized(lock)
                    {
                        lock.notifyAll();
                    }
                }
                prompt = currentChat!=null ? currentChat.accountName : "lettera";
            }
        }    
        finally
        {
            db.close();
        }
    }

    private void ansiTitle(String value)
    {
        if(console)
        {
            Utils.ansiTitle(value);
        }
    }

    private LineReader buildLineReader(Completer completer)
    {
        LineReaderBuilder builder = LineReaderBuilder.builder().terminal(terminal);
        return (completer!=null ? builder.completer(completer) : builder)
               .option(LineReader.Option.CASE_INSENSITIVE, true)
               .option(LineReader.Option.AUTO_FRESH_LINE, true)
               .build();
    }

    private void showHelp()
    {
        reader.printAbove("");
        reader.printAbove(HELP);
        reader.printAbove("");
    }
            
    private void showHelpTip()
    {
        reader.printAbove("");
        reader.printAbove("Type '/help' to show complete help.");
        reader.printAbove("");
    }
            
    private Completer getCommandsCompleter()
    {
        String[] secEmails = getEmails(secs);
        String[] pubEmails = getEmails(pubs);
        String[] sessions = getSessions();

        ArrayList<Object> list = new ArrayList<>();

        list.add(_HELP);
        
        list.add(_SETUP_ACCOUNT);
        list.add(_SETUP_FRIEND);
//        list.add(node((Object[]) secEmails));
        
//        for(String from : secEmails)
//        {
//            list.add(node(from, node(excludeEmail(pubEmails, from).toArray())));
//        }

        list.add(_LIST_ACCOUNTS);
        list.add(_LIST_FRIENDS);
        list.add(_LIST_CHATS);

        if(sessions.length>0)
        {
            list.add(node((Object[]) sessions));
        }
        list.add(_CHAT);
        if(sessions.length>0)
        {
            list.add(node((Object[]) sessions));
        }

        list.add(_UNREAD);

        list.add(_WIZARD);
        
        list.add(_EXIT);
        
        return new Completers.TreeCompleter(node(list.toArray()));
    }
    private Completer getSecEmailsCompleter()
    {
        String[] secEmails = getEmails(secs);
        return new StringsCompleter(secEmails);
    }
    private Completer getPubEmailsCompleter()
    {
        String[] pubEmails = getEmails(pubs);
        return new StringsCompleter(pubEmails);
    }

    private Completer getAccountCompleter()
    {
        Account[] accounts = db.getAccounts();
        ArrayList<String> names = new ArrayList<>();
        for(Account item : accounts)
        {
            names.add(item.name);
        }
        Collections.sort(names);
        return new StringsCompleter(names.toArray(new String[0]));
    }

    private Completer getFriendCompleter()
    {
        Friend[] friends = db.getFriends();
        ArrayList<String> names = new ArrayList<>();
        for(Friend item : friends)
        {
            names.add(item.name);
        }
        Collections.sort(names);
        return new StringsCompleter(names.toArray(new String[0]));
    }
    
    private void loadAllKeys() throws IOException, InterruptedException
    {
        secs = GPG.getSecKeys();
        pubs = GPG.getPubKeys();
    }
            
    static final Pattern EMAIL_PATTERN1 = Pattern.compile(".*<(.+@.+)>.*");
    static final Pattern EMAIL_PATTERN2 = Pattern.compile("([^<>]+@[^<>]+)");

    private static String getEmail(String uid)
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
    
    private static String[] getEmails(MainKey[] keys)
    {
        ArrayList<String> emails = new ArrayList<>();
        for(MainKey item : keys)
        {
            for(UserId uid : item.getUids())
            {
                String email = getEmail(uid.uid);
                if(email!=null)
                {
                    emails.add(email);
                }
            }
        }
        Collections.sort(emails);
        return emails.toArray(new String[0]);
    }

    private String[] getSessions()
    {
        return new String[0];
    }

    static final String DEF_GMAIL_SMTP_HOST = "smtp.gmail.com";
    static final int DEF_GMAIL_SMTP_PORT = 587;
    
    static final String DEF_GMAIL_IMAP_HOST = "imap.gmail.com";
    static final int DEF_GMAIL_IMAP_PORT = 993;
    
    static final String DEF_GMAIL_POP3_HOST = "pop.gmail.com";
    static final int DEF_GMAIL_POP3_PORT = 995;
    
    private Account setupAccount() throws GeneralSecurityException
    {
        ansiTitle("setup-account");
        
        LineReader lineReader = buildLineReader(null);
        String name = lineReader.readLine("name: ");
        if(name.isEmpty())
        {
            return null;
        }

        String address="";
        String smtpHost="";
        int smtpPort=0;
        String imapHost="";
        int imapPort=0;
        String pop3Host="";
        int pop3Port=0;
        boolean auth = true; 
        boolean starttls = true;
        String username="";
        String emailPass="";
        String keyid="";
        String gpgPass="";
                
        Account account = db.getAccount(name);
        if(account!=null)
        {
            address = account.address;
            smtpHost = account.smtpHost;
            
            smtpPort = account.smtpPort;
            imapHost = account.imapHost;
            imapPort = account.imapPort;
            pop3Host = account.pop3Host;
            pop3Port = account.pop3Port;
            auth = account.auth;
            starttls = account.starttls;
            username = account.username;
            keyid = account.keyid;
        }

        lineReader = buildLineReader(getSecEmailsCompleter());
        address = readLineEmail(lineReader, "address: ", address);
        if(address.isEmpty())
        {
            return null;
        }
        if(address.contains("@gmail.com"))
        {
            smtpHost = Strings.firstNonEmpty(smtpHost, DEF_GMAIL_SMTP_HOST);
            smtpPort = smtpPort>0 ? smtpPort : DEF_GMAIL_SMTP_PORT;
            imapHost = Strings.firstNonEmpty(imapHost, DEF_GMAIL_IMAP_HOST);
            imapPort = imapPort>0 ? imapPort : DEF_GMAIL_IMAP_PORT;
            pop3Host = Strings.firstNonEmpty(pop3Host, DEF_GMAIL_POP3_HOST);
            pop3Port = pop3Port>0 ? pop3Port : DEF_GMAIL_POP3_PORT;
            username = Strings.firstNonEmpty(username, address, "");
        }
        
        // SMTP
        smtpHost = readString("smtp.host: ", smtpHost);
        if(smtpHost.isEmpty())
        {
            return null;
        }
        smtpPort = readPort("smtp.port: ", smtpPort);
        if(smtpPort==0)
        {
            return null;
        }
        
        // IMAP
        imapHost = readString("imap.host: ", imapHost);
        if(imapHost.isEmpty())
        {
            return null;
        }
        imapPort = readPort("imap.port: ", imapPort);
        if(imapPort==0)
        {
            return null;
        }
        
        // POP3
        pop3Host = readString("pop3.host: ", pop3Host);
        if(pop3Host.isEmpty())
        {
            return null;
        }
        pop3Port = readPort("pop3.port: ", pop3Port);
        if(pop3Port==0)
        {
            return null;
        }
        
        auth = readBoolean("auth: ", auth);
        starttls = readBoolean("starttls: ", starttls);
        
        username = readString("username: ",username);
        if(username.isEmpty())
        {
            return null;
        }
        
        char[] password = readPasswordOrPass("password: ");
        if(password.length==0 && account!=null)
        {
            emailPass=account.emailPass;
        }
        else
        {
            emailPass=wrapKey("email", name, password);
        }
        lineReader.zeroOut();
        
        String text = keyid;
        try
        {
            SecKey[] sec = GPG.getSecKeys(name, address);
            if(sec.length>0)
            {
                SubKey main = sec[0].getMain();
                String keyid2 = main.keyid;
                if(keyid==null || !keyid.equalsIgnoreCase(keyid2))
                {
                    keyid = keyid2;
                    text = secKeyToPlaintext(sec[0]);
                }
            }
        }
        catch (IOException | InterruptedException ex)
        {
            System.getLogger(TerminalChat.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        lineReader.printAbove("key: "+text);

        password = readPassword("key-passphrase:" );
        if(password.length==0 && account!=null)
        {
            gpgPass=account.gpgPass;
        }
        else
        {
            gpgPass=wrapKey("gpg", name, password);
        }
        lineReader.zeroOut();
        
        account = new Account(name, address, auth, starttls, smtpHost, smtpPort, imapHost, imapPort, pop3Host, pop3Port, username, emailPass, keyid, gpgPass);
        
        this.db.putAccount(account);
        this.db.commit();
       
        return account;        
    }
    
    private Friend setupFriend()
    {
        ansiTitle("setup-friend");
        
        LineReader lineReader = buildLineReader(null);
        String name = lineReader.readLine("name: ");
        if(name.isEmpty())
        {
            return null;
        }

        String address="";
        String keyid="";
                
        Friend friend = this.db.getFriend(name);
        if(friend!=null)
        {
            address = friend.address;
            keyid = friend.keyid;
        }

        lineReader = buildLineReader(getPubEmailsCompleter());
        address = readLineEmail(lineReader, "address: ", address);
        if(address.isEmpty())
        {
            return null;
        }
        
        lineReader.zeroOut();
        
        String text = keyid;
        try
        {
            PubKey[] pub = GPG.getPubKeys(name, address);
            if(pub.length>0)
            {
                SubKey main = pub[0].getMain();
                String keyid2 = main.keyid;
                if(keyid==null || !keyid.equalsIgnoreCase(keyid2))
                {
                    keyid = keyid2;
                    text = pubKeyToPlaintext(pub[0]);
                }
            }
        }
        catch (IOException | InterruptedException ex)
        {
            System.getLogger(TerminalChat.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        lineReader.printAbove("key: "+text);
        
        friend = new Friend(name, address, keyid);
        
        this.db.putFriend(friend);
        this.db.commit();
        
        return friend;
        
    }
    
    private String setupChat()
    {
        ansiTitle("setup-chat");
        
        LineReader lineReader = buildLineReader(getAccountCompleter());
        String accountName = lineReader.readLine("account: ");
        if(accountName.isEmpty())
        {
            return null;
        }

        Account account = this.db.getAccount(accountName);
        if(account==null)
        {
            return null;
        }
        lineReader.printAbove(account.name+" / "+account.address+" / "+account.keyid);

        lineReader = buildLineReader(getFriendCompleter());
        String friendName = lineReader.readLine("friend: ");
        if(friendName.isEmpty())
        {
            return null;
        }

        Friend friend = this.db.getFriend(friendName);
        if(friend==null)
        {
            return null;
        }
        lineReader.printAbove(friend.name+" / "+friend.address+" / "+friend.keyid);
        
        lineReader.zeroOut();
        
        Chat chat = new Chat(account.name, account.address, account.keyid, friend.name, friend.address, friend.keyid);
        
        this.db.putChat(chat);
        this.db.commit();
        return chat.id;
    }

    private static String readLineEmail(LineReader lineReader, String prompt, String buffer)
    {
        for(;;)
        {
            String email=lineReader.readLine(prompt, null, buffer);
            email = email.trim();
            if(email.isEmpty())
            {
                return email;
            }
            try
            {
                new InternetAddress(email).validate();
                return email;
            }
            catch (AddressException ex)
            {
                lineReader.printAbove("not an email");
            }
        }
    }
    
    private String readString(String prompt, String buffer)
    {
        LineReader lineReader = buildLineReader(null);
        for(;;)
        {
            String host = lineReader.readLine(prompt, null, buffer);
            if(host.isEmpty())
            {
                return host;
            }
            host = host.trim();
            if(!host.isEmpty())
            {
                return host;
            }
        }
    }
    
    private int readPort(String prompt, int buffer)
    {
        LineReader lineReader = buildLineReader(null);
        for(;;)
        {
            String port = lineReader.readLine(prompt, null, buffer>0? Integer.toString(buffer):null);
            if(port.isEmpty())
            {
                return 0;
            }
            port = port.trim();
            int n = Parsers.safeParseInt(port, 0);
            if(n>0)
            {
                return n;
            }
        }
    }

    private boolean readBoolean(String prompt, boolean buffer)
    {
        LineReader lineReader = buildLineReader(null);
        for(;;)
        {
            String yn = lineReader.readLine(prompt, null, buffer? "Y":"N");
            if(yn.isEmpty())
            {
                return false;
            }
            yn = yn.trim();
            if(yn.equalsIgnoreCase("Y"))
            {
                return true;
            }
            if(yn.equalsIgnoreCase("N"))
            {
                return false;
            }
        }
    }

    private String secKeyToPlaintext(SecKey sc)
    {
        StringBuilder sb = new StringBuilder();
        SubKey main = sc.getMain();
        sb.append(main.keyid).append('/').append(main.capabilities);
        StringJoiner sj = new StringJoiner(","," ","");
        for(UserId uid : sc.getUids())
        {
            sj.add(uid.uid);
        }
        return sb.append(sj).toString();
    }
    private String pubKeyToPlaintext(PubKey pk)
    {
        StringBuilder sb = new StringBuilder();
        SubKey main = pk.getMain();
        sb.append(main.keyid).append('/').append(main.capabilities);
        StringJoiner sj = new StringJoiner(","," ","");
        for(UserId uid : pk.getUids())
        {
            sj.add(uid.uid);
        }
        return sb.append(sj).toString();
    }

    private static final String HR = "----------------------------------------";

    private int listAccounts()
    {
        Account[] items = db.getAccounts();
        Table table = new Table(items.length, 3, false);
        for(int r=0;r<items.length;r++)
        {
            table.setCell(r,0, items[r].name);
            table.setCell(r,1, items[r].address);
            table.setCell(r,2, items[r].keyid);
        }
        System.out.println(HR);
        System.out.println("Accounts: "+items.length);
        System.out.println(table.toString());
        return items.length;
    }

    private int listFriends()
    {
        Friend[] items = db.getFriends();
        Table table = new Table(items.length, 3, false);
        for(int r=0;r<items.length;r++)
        {
            table.setCell(r,0, items[r].name);
            table.setCell(r,1, items[r].address);
            table.setCell(r,2, items[r].keyid);
        }
        System.out.println(HR);
        System.out.println("Friends: "+items.length);
        System.out.println(table.toString());
        return items.length;
    }

    private int listChats()
    {
        Chat[] items = db.getChats();
        Table table = new Table(items.length, 3, false);
        for(int r=0;r<items.length;r++)
        {
            table.setCell(r,0, items[r].id);
            table.setCell(r,1, items[r].accountAddress);
            table.setCell(r,2, items[r].friendAddress);
        }
        System.out.println(HR);
        System.out.println("Chats: "+items.length);
        System.out.println(table.toString());
        return items.length;
    }

    private String startChat(String session)
    {
        Chat chat = db.getChat(session);
        if(chat==null)
        {
            return null;
        }
//666        verificar que no han cambiado las direcciones ni las keyid
                
        currentAccount = db.getAccount(chat.accountName);
        currentFriend = db.getFriend(chat.friendName);
        
        Chat chat2 = Chat.build(currentAccount, currentFriend);

        if(!chat.equals(chat2))
        {
            System.err.println("WARNING: FIELDS CHANGED");
            System.err.println(chat.diff(chat2));
        }
        
        currentChat = chat;
        currentNotes = db.getNotes(session);
        
        SecureChars secureEmailPass = new SecureChars(unwrapKey("email", currentAccount.name, currentAccount.emailPass));
        SecureChars secureGpgPass = new SecureChars(unwrapKey("gpg", currentAccount.name, currentAccount.gpgPass));
        
        mailReader = new IMAP(currentAccount.imapHost, currentAccount.imapPort, currentAccount.auth, currentAccount.starttls, false, currentAccount.username, secureEmailPass);
        smtp = new SMTP(currentAccount.smtpHost, currentAccount.smtpPort, currentAccount.auth, currentAccount.starttls, currentAccount.username, secureGpgPass, currentAccount.address);

        Thread syncThread = new Thread(chatSync, "chatSync");
        syncThread.setDaemon(true);
        syncThread.start();       
        return chat.accountName;
    }
    
    private volatile boolean chatActive;
    private final BlockingQueue<Note> chatQueue = new LinkedBlockingQueue<>(8);
    private final Object lock = new Object();
    
    static final int LOOP_MILLIS = 5_000;
    private final Runnable chatSync = new Runnable()
    {
        @Override
        public void run()
        {
            int waitMillis = LOOP_MILLIS;
            chatActive=true;
            Date after=null;
            while(chatActive)
            {
                synchronized(lock)
                {
                    waitMillis *= 2;
                    try
                    {
                        Note note;
                        while((note=chatQueue.poll())!=null)
                        {
                            byte[] plainBytes = note.text.getBytes(StandardCharsets.UTF_8);
                            byte[] encryptedText = GPG.encryptAndSign(plainBytes, currentChat.accountAddress, null, currentChat.friendAddress);
                            currentNotes.put(JavaTime.epochSecond(), note);
                            if(!smtp.isConnected())
                            {
                                smtp.connect();
                            }
                            String subject = "lettera "+currentChat.accountKeyid+"-"+currentChat.friendKeyid;
                            smtp.send(subject, new String(encryptedText,StandardCharsets.UTF_8), currentChat.friendAddress);
                            waitMillis = LOOP_MILLIS;
                        }
                        if(mailReader.isConnected())
                        {
                            mailReader.connect();
                        }
                        Message[] messages = after!=null ? mailReader.getMessages(after) : mailReader.getMessages();
                        for(Message item : messages)
                        {
                            System.err.println(item);
                        }
                        lock.wait(waitMillis);
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
        }
    };

    private char[] readPassword(String fmt, Object... args)
    {
        VirtualConsole console = AbstractConsole.getInstance(debug);
        return console.readPassword(fmt, args);
    }

    private char[] readPasswordOrPass(String fmt, Object... args)
    {
        char[] password = readPassword(fmt, args);
        if(Chars.startsWith(password, "pass:"))
        {
            String path = new String(password).replaceFirst("pass:", "");
            return PASS.getKey(path).toCharArray();
        }
        return password;
    }

    private String wrapKey(String purpose, String name, char[] password)
    {
        String purposeName = purpose+"+"+name;
        byte[] pass = Byter.bytesUTF8(password);
        String wrapped = wrapper.wrap(pass, purposeName);
        Arrays.fill(pass, (byte)0);
        return wrapped;
    }
    
    private char[] unwrapKey(String purpose, String name, String wrapped)
    {
        String purposeName = purpose+"+"+name;
        byte[] pass = wrapper.unwrap(wrapped, purposeName);
        char[] password = Byter.charsUTF8(pass);
        Arrays.fill(pass, (byte)0);
        return password;
    }    
}
