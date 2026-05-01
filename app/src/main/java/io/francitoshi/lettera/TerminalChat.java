/*
 *  TerminalChat.java
 *
 *  Copyright (c) 2025-2026 francitoshi@gmail.com
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
import io.nut.base.crypto.gpg.MainKey;
import io.nut.base.crypto.gpg.PASS;
import io.nut.base.crypto.gpg.PubKey;
import io.nut.base.crypto.gpg.SecKey;
import io.nut.base.crypto.gpg.SubKey;
import io.nut.base.crypto.gpg.UserId;
import io.nut.base.encoding.Base64DecoderException;
import io.nut.base.encoding.Hex;
import io.nut.base.figletter.FigLetter;
import io.nut.base.io.IO;
import io.nut.base.io.console.AbstractConsole;
import io.nut.base.net.Emails;
import io.nut.base.resources.ResourceBundles;
import io.nut.base.security.SecureChars;
import io.nut.base.time.JavaTime;
import io.nut.base.util.Chars;
import io.nut.base.util.Parsers;
import io.nut.base.util.Strings;
import io.nut.base.util.Utils;
import io.nut.base.util.concurrent.hive.Hive;
import io.nut.core.net.mail.MailReader;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
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
import java.util.Collections;
import java.util.Locale;
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
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;

/**
 *
 * @author franci
 */
public class TerminalChat extends Lettera
{
    //https://patorjk.com/software/taag/#p=display&f=miniwi&t=lettera

    static final int LOOPS = 5;
    private static final String HELP;
    static 
    {
        ResourceBundle bundle = ResourceBundle.getBundle(TerminalChat.class.getName(), Locale.getDefault());
        
        String helpFileName = bundle.getString("help");
        HELP = ResourceBundles.getResourceAsString(TerminalChat.class, helpFileName, "help");
    }

//    final Bee<Note> chatBee; 
//    final Bee<Integer> imapBee; 
//    final Bee<Note> smtpBee; 

///666    MOVER VARIALES MIEMBRO A LETTERA Y ADAPTAR CONSTRUCTOR
///666    CONFIGURAR UNA CUENTA MEDIANTE COMANDO        
    private final Terminal terminal;
    
    private volatile LineReader reader;
    private volatile MailReader mailReader;
//666    private volatile SMTP smtp;
    private volatile boolean wizard;
    
    private volatile boolean chatActive;
    private final Object lock = new Object();
    
        
    public TerminalChat(Terminal terminal, File configFile, File keystoreFile, File letteraDb, SecureChars passphrase, boolean mock, boolean debug)
    {
        super(IO.asPrintStream(terminal.output()), configFile, keystoreFile, letteraDb, passphrase, mock, debug);
        this.terminal = terminal;
    }
    
    public void setWizard(boolean value)
    {
        this.wizard = value;
    }
    
    private static final String _HELP = "/help";
    private static final String _ABOUT = "/about";
    private static final String _SETUP_ACCOUNT = "/setup-account";
    private static final String _SETUP_FRIEND = "/setup-friend";
    private static final String _SETUP_WIZARD = "/setup-wizard";
    private static final String _IMPORT_ACCOUNTS = "/import-accounts";
    private static final String _IMPORT_FRIENDS = "/import-friends";
    private static final String _LIST_ACCOUNTS = "/list-accounts";
    private static final String _LIST_FRIENDS = "/list-friends";
    private static final String _LIST_CHATS = "/list-chats";
    private static final String _CHAT = "/chat";
    private static final String _UNREAD = "/unread";
    private static final String _WAIT_MESSAGE = "/wait-message";
    private static final String _PASSPHRASE = "/passphrase";
    private static final String _EXIT = "/exit";

    
    private final Object waitMessageLock = new Object();

    @Override
    public TerminalChat open() throws IOException, Base64DecoderException, InterruptedException, KeyStoreException, NoSuchAlgorithmException, CertificateException, Exception
    {
        return (TerminalChat) super.open();
    }
    
    void run() throws IOException, InterruptedException, Exception
    {
        //.add(storeBee, printBee);
        reader = buildLineReader(getCommandsCompleter());

        try
        {
            ansiTitle("lettera");
            if(wizard)
            {
                boolean wa = countAccounts()==0 && countSecKeys()>0;
                boolean wf = countFriends()==0 && countPubKeys()>0;
                wizardSetup(wa, wf);
            }

            listChats();
            
            showHelp();

            showHelpTip();
            String prompt = "lettera";
            String line;
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
                        this.out.println(Main.WELCOME_TXT);
                    }
                    else if (line.startsWith(_SETUP_ACCOUNT))
                    {
                        setupAccount();
                    }
                    else if (line.startsWith(_SETUP_FRIEND))
                    {
                        setupFriend();
                    }
                    else if (line.startsWith(_IMPORT_ACCOUNTS))
                    {
                        importAccounts();
                    }
                    else if (line.startsWith(_SETUP_WIZARD))
                    {
                        boolean wa = countAccounts()==0 && countSecKeys()>0;
                        boolean wf = countFriends()==0 && countPubKeys()>0;
                        wizardSetup(wa, wf);
                    }
                    else if (line.startsWith(_IMPORT_FRIENDS))
                    {
                        importFriends();
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
                            startChat(args[1], Mode.ReadWrite);
                        }
                        else
                        {
                            startChat(setupChat(), Mode.ReadWrite);
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
                    else if (line.startsWith(_WAIT_MESSAGE))
                    {
                        synchronized (waitMessageLock)
                        {
                            reader.printAbove("LOCK BY WAIT-MESSAGE LOCK");
                            waitMessageLock.wait(3600_000);
                        }
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
                    long id = Note.NONCE.get();
                    Note note = new Note(id, JavaTime.epochSecond(), currentChat.id, currentChat.accountAddress, currentChat.friendAddress, currentChat.accountKeyid, currentChat.friendKeyid, 0, 0, line);
                    sessionHub.send(note);
                }
                prompt = currentChat!=null ? currentChat.accountName : "lettera";
            }
        }    
        finally
        {
            db.close();
        }
    }

    public void banner(String text)
    {
        //"smblock.tlf","kompaktblk.flf","miniwi.flf","terminus.flf"
        FigLetter fl;
        try
        {
            fl = FigLetter.getInstance("miniwi", Main.class.getResourceAsStream("miniwi.flf"), 1);
            String s = fl.render(text);
            reader.printAbove(s);
        }
        catch (IOException ex)
        {
            System.getLogger(TerminalChat.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
    }
    
    private void ansiTitle(String value)
    {
        if(console)
        {
            Utils.ansiTitle(value);
        }
        reader.printAbove("--------------------------------------------------");
        banner(value);
        reader.printAbove("--------------------------------------------------");
    }

    private LineReader buildLineReader(Completer completer)
    {
        if(console)
        {
            LineReaderBuilder builder = LineReaderBuilder.builder().terminal(terminal);
            LineReader lineReader = (completer!=null ? builder.completer(completer) : builder)
                   .option(LineReader.Option.CASE_INSENSITIVE, true)
                   .option(LineReader.Option.AUTO_FRESH_LINE, true)
                   .build();
            return lineReader;
        }
        else
        {
            return new MockLineReader(this.terminal.input(), this.terminal.output());
        }
    }

    enum YesNoMode
    {
        Yn("[Y/n]"), yN("[n/N]"), yn("[y/n]");
        final String text;
        private YesNoMode(String text)
        {
            this.text = text;
        }
    }
    int readYesOrNo(String prompt, YesNoMode mode, int loops) throws UserInterruptException, EndOfFileException
    {
        for(int i=0;i<loops;i++)
        {
            LineReader lineReader = buildLineReader(null);
            String yn = lineReader.readLine(prompt+mode.text+": ", null, null).trim();
            if(yn.equalsIgnoreCase("y") || (yn.isEmpty() && mode==YesNoMode.Yn))
            {
                return 1;
            }
            if(yn.equalsIgnoreCase("n") || (yn.isEmpty() && mode==YesNoMode.yN))
            {
                return 0;
            }
        }
        return 0;
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
        list.add(_SETUP_WIZARD);
//        list.add(node((Object[]) secEmails));
        
//        for(String from : secEmails)
//        {
//            list.add(node(from, node(excludeEmail(pubEmails, from).toArray())));
//        }

        list.add(_IMPORT_ACCOUNTS);
        list.add(_IMPORT_FRIENDS);
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
        list.add(_WAIT_MESSAGE);
        
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
        return setupAccount(null, null, null);
    }
    private Friend setupFriend() throws GeneralSecurityException
    {
        return setupFriend(null, null, null);
    }

    private Account setupAccount(String name, String address, String keyid) throws GeneralSecurityException
    {
        ansiTitle("setup-account");
        LineReader lineReader = buildLineReader(null);
        name = name!=null ? lineReader.readLine("name: ", null, name) : lineReader.readLine("name: ");
        if(name.isEmpty())
        {
            return null;
        }

        address = address!=null ? address : "";
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
        keyid="";
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
        
        auth = readBoolean("auth[Y/n]: ", "","y", "n", auth);
        starttls = readBoolean("starttls[Y/n]: ", "","y", "n", starttls);
        
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
            emailPass=keyWrapper.wrapKey("email", name, password);
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
            gpgPass=keyWrapper.wrapKey(GPG_PURPOSE, name, password);
        }
        lineReader.zeroOut();
        
        account = new Account(name, address, auth, starttls, smtpHost, smtpPort, imapHost, imapPort, pop3Host, pop3Port, username, emailPass, keyid, gpgPass);
        
        this.db.putAccount(account);
        this.db.commit();
       
        return account;        
    }    
    
    private Friend setupFriend(String name, String address, String keyid)
    {
        ansiTitle("setup-friend");
        
        LineReader lineReader = buildLineReader(null);
        name = name!=null ? lineReader.readLine("name: ", null, name) : lineReader.readLine("name: ");
        if(name.isEmpty())
        {
            return null;
        }

        address="";
        keyid="";
                
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

    private void wizardSetup(boolean accounts, boolean friends) throws GeneralSecurityException, IOException, InterruptedException
    {
        ansiTitle("wizard");
        if(accounts)
        {
            int yn = readYesOrNo("You can import your gpg secrect keys as accounts.\nDo you want to import them now?", YesNoMode.Yn, LOOPS);
            if(yn==1)
            {
                reader.printAbove("lettera>"+_IMPORT_ACCOUNTS);
                importAccounts();
            }
        }
        if(friends)
        {
            int yn = readYesOrNo("You can import your gpg public keys as friends.\nDo you want to import them now?", YesNoMode.Yn, LOOPS);
            if(yn==1)
            {
                this.out.println("lettera>"+_IMPORT_FRIENDS);
                importFriends();
            }
        }
        if(accounts && countAccounts()==0)
        {
            int yn = readYesOrNo("Do you want to create a new account?", YesNoMode.Yn, LOOPS);
            if(yn==1)
            {
                this.out.println("lettera>"+_SETUP_ACCOUNT);
                setupAccount();
            }
        }
        if(friends && countFriends()==0)
        {
            int yn = readYesOrNo("Do you want to create a new friend?", YesNoMode.Yn, LOOPS);
            if(yn==1)
            {
                this.out.println("lettera>"+_SETUP_FRIEND);
                setupFriend();
            }
        }
    }

    private void importAccounts() throws IOException, InterruptedException, GeneralSecurityException 
    {
        ansiTitle("import-accounts");
        SecKey[] items = GPG.getSecKeys();
        for(int r=0;r<items.length;r++)
        {
            UserId[] uids = items[r].getUids();
            String keyid = items[r].getMain().keyid;
            for(int u=0;u<uids.length;u++)
            {
                reader.printAbove("uid: "+uids[u].uid);
                reader.printAbove("keyid: "+keyid);
                if(1==readYesOrNo("Import?", YesNoMode.Yn, LOOPS))
                {
                    String[] nameEmail = Emails.parseEmailAddress(uids[u].uid);
                    setupAccount(nameEmail[0], nameEmail[1], keyid);
                }
            }
        }
    }
    private void importFriends() throws IOException, InterruptedException, GeneralSecurityException
    {
        ansiTitle("import-friends");
        PubKey[] items = GPG.getPubKeys();
        for(int r=0;r<items.length;r++)
        {
            UserId[] uids = items[r].getUids();
            String keyid = items[r].getMain().keyid;
            for(int u=0;u<uids.length;u++)
            {
                reader.printAbove("uid: "+uids[u].uid);
                reader.printAbove("keyid: "+keyid);
                if(1==readYesOrNo("Import?", YesNoMode.Yn, LOOPS))
                {
                    String[] nameEmail = Emails.parseEmailAddress(uids[u].uid);
                    setupFriend(nameEmail[0], nameEmail[1], keyid);
                }
            }
        }
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
        
        lineReader.printAbove("You need a shared secret with your friend to verify that there's no man-in-the-middle attack. You can skip this, but you'll have to verify the keys are correct yourself.");

        char[] sharedSecret = readPasswordOrPass("shared secret: ");
        String mutualAuthProof = null;
        if(sharedSecret!=null && sharedSecret.length!=0)
        {
            long t0 = System.nanoTime();

            byte[] salt = (account.keyid+friend.keyid).getBytes(StandardCharsets.UTF_8);
            byte[] sharedSecret2 = ARGON2.rawHash(128, 64*1024, 1, sharedSecret, salt);
            byte[][] hash = KRIPTO.deriveMutualAuthProof(Hex.decode(account.keyid), Hex.decode(friend.keyid), sharedSecret2);
            mutualAuthProof = Hex.encode(hash[0])+"-"+Hex.encode(hash[1]);

            long t1 = System.nanoTime();
            if(debug)
            {
                this.out.printf("argon2 = %d ms %s\n", TimeUnit.NANOSECONDS.toMillis(t1-t0), mutualAuthProof);
            }
        }
        lineReader.zeroOut();
        
        Chat chat = new Chat(account.name, account.address, account.keyid, friend.name, friend.address, friend.keyid, mutualAuthProof);
        
        this.db.putChat(chat);
        this.db.commit();
        return chat.id;
    }

    private String readLineEmail(LineReader lineReader, String prompt, String buffer)
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
    
    private String readLine(String prompt, Character ch, String buffer)
    {
        LineReader lineReader = buildLineReader(null);
        for(;;)
        {
            String line = lineReader.readLine(prompt, ch, buffer);
            if(line.isEmpty())
            {
                return line;
            }
            line = line.trim();
            if(!line.isEmpty())
            {
                return line;
            }
        }
    }
    private String readString(String prompt, String buffer)
    {
        return readLine(prompt, null, buffer);
    }    
    private char[] readPassword(String prompt)
    {
        if(console)
        {
            return AbstractConsole.getInstance(mock).readPassword("%s", prompt);
        }
        else
        {
            return readLine(prompt, '*', "").toCharArray();
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

    private boolean readBoolean(String prompt, String buffer, String yes, String no, boolean defaultValue)
    {
        LineReader lineReader = buildLineReader(null);
        for(;;)
        {
            String yn = lineReader.readLine(prompt, null, buffer);
            if(yn.isEmpty())
            {
                return defaultValue;
            }
            yn = yn.trim();
            if(yn.equalsIgnoreCase(yes))
            {
                return true;
            }
            if(yn.equalsIgnoreCase(no))
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
        
    private void printMessage(Message message, char[] gpgPass)
    {
        try
        {
            if (message.isMimeType("text/plain"))
            {
                String body = (String) message.getContent();
                GPG.DecryptStatus status = new GPG.DecryptStatus();
                byte[] plaintext = GPG.decryptAndVerify(body.getBytes(UTF8), gpgPass, status);
//                currentChat.friendKeyid
                reader.printAbove(currentChat.friendName+"> "+new String(plaintext,UTF8));
            }
            else if (message.isMimeType("text/html"))
            {
                String bodyHtml = (String) message.getContent();
                reader.printAbove("Cuerpo del mensaje (HTML):");
                reader.printAbove(bodyHtml);
                // Podrías usar una librería como Jsoup para parsear este HTML
            }
            else if (message.isMimeType("multipart/*"))
            {
                Multipart multipart = (Multipart) message.getContent();
                reader.printAbove("Este es un mensaje multipart con " + multipart.getCount() + " partes.");

                // Iteramos sobre cada parte
                for (int i = 0; i < multipart.getCount(); i++)
                {
                    BodyPart bodyPart = multipart.getBodyPart(i);

                    // --- IMPORTANTE: Ignorar archivos adjuntos ---
                    // Si la disposición es ATTACHMENT, probablemente no es el cuerpo principal.
                    if (Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition()))
                    {
                        reader.printAbove("Parte " + i + " es un adjunto: " + bodyPart.getFileName());
                        continue; // Pasamos a la siguiente parte
                    }

                    // Verificamos si la parte es texto plano o HTML
                    if (bodyPart.isMimeType("text/plain"))
                    {
                        reader.printAbove("Cuerpo encontrado (Texto Plano en multipart):");
                        reader.printAbove(bodyPart.getContent().toString());

                    }
                    else if (bodyPart.isMimeType("text/html"))
                    {
                        reader.printAbove("Cuerpo encontrado (HTML en multipart):");
                        reader.printAbove(bodyPart.getContent().toString());
                    }
                }
            }
                
        }
        catch (IOException | MessagingException | InterruptedException ex)
        {
            System.getLogger(TerminalChat.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
    }

    private char[] readPasswordOrPass(String prompt)
    {
        if(console)
        {
            char[] password = readPassword(prompt);
            if(Chars.startsWith(password, "pass:"))
            {
                String path = new String(password).replaceFirst("pass:", "");
                return PASS.getKey(path).toCharArray();
            }
            return password;
        }
        else
        {
            return readLine(prompt, '*', "").toCharArray();
        }
    }    
    @Override
    protected void receive(Note note)
    {
        reader.printAbove(note.text);
    }
}
