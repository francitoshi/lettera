/*
 * Copyright (C) 2025-2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.lettera;

import io.francitoshi.lettera.data.Friend;
import io.francitoshi.lettera.data.Sender;
import static io.francitoshi.lettera.Lettera.UTF8;
import io.francitoshi.lettera.data.PlainNote;
import io.francitoshi.lettera.email.EmailProviders;
import io.francitoshi.lettera.email.EmailPreset;
import io.nut.base.crypto.gpg.GPG;
import static io.nut.base.crypto.gpg.GPG.CA;
import static io.nut.base.crypto.gpg.GPG.CURVE25519;
import static io.nut.base.crypto.gpg.GPG.E;
import io.nut.base.crypto.gpg.MainKey;
import io.nut.base.crypto.gpg.PASS;
import io.nut.base.crypto.gpg.PubKey;
import io.nut.base.crypto.gpg.SecKey;
import io.nut.base.crypto.gpg.SubKey;
import io.nut.base.crypto.gpg.UserId;
import io.nut.base.encoding.Base64DecoderException;
import io.nut.base.figletter.FigLetter;
import io.nut.base.io.IO;
import io.nut.base.io.console.AbstractConsole;
import io.nut.base.net.Emails;
import io.nut.base.i18n.ResourceBundles;
import io.nut.base.security.SecureChars;
import io.nut.base.util.Parsers;
import io.nut.base.lang.Strings;
import io.nut.base.util.Utils;
import io.nut.base.concurrent.actor.ActorHub;
import io.nut.base.util.Args;
import io.nut.base.util.As;
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
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.StringJoiner;
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

    private final Terminal terminal;
    
    private volatile LineReader reader;
    private volatile MailReader mailReader;

    private volatile boolean wizard;
    
    private volatile Chat currentChat;
    private volatile boolean chatActive;
    private final Object lock = new Object();
        
    public TerminalChat(ActorHub hive, Terminal terminal, File letteraDir, String username, SecureChars passphrase, boolean passpath, boolean mock, boolean debug)
    {
        super(hive, IO.asPrintStream(terminal.output()), letteraDir, username, passphrase, passpath, mock, debug);
        this.terminal = terminal;
    }
    
    public void setWizard(boolean value)
    {
        this.wizard = value;
    }
    
    private static final String _HELP = "/help";
    private static final String _ABOUT = "/about";
    private static final String _SENDER = "/sender";
    private static final String _CHAT = "/chat";
    private static final String _CHAT_RM = "/chat-rm";
    private static final String _PROXY = "/proxy";
    private static final String _TORIFY = "/torify";
    private static final String _FRIEND = "/friend";
    private static final String _FRIEND_RM = "/friend-rm";
    private static final String _FRIEND_LS = "/friend-ls";
    private static final String _EXIT = "/exit";
    
    private static final String _UNREAD = "/unread";
    private static final String _WAIT_MESSAGE = "/wait-message";
    private static final String _PASSPHRASE = "/passphrase";
    
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
            Sender sender = db.getSender();
            if (wizard && (sender==null || !sender.isValid()))
            {
                sender = setupSender();
            }
            
            ansiTitle("lettera");
                
            listSender();
            listFriends();
            
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
                    else if (line.startsWith(_SENDER))
                    {
                        setupSender();
                    }
                    else if (line.startsWith(_CHAT))
                    {
                        chat(line);
                    }
                    else if (line.startsWith(_FRIEND))
                    {
                        setupFriend(null);
                    }
                    else if (line.startsWith(_FRIEND_RM))
                    {
                        reader.printAbove("");
                        reader.printAbove("Not Yet Implemented!!!");
                        reader.printAbove("");
//                        setupFriend();
                    }
                    else if (line.startsWith(_FRIEND_LS))
                    {
                        listFriends();
                    }
                    else if (line.startsWith(_UNREAD))
                    {
                        reader.printAbove("");
                        reader.printAbove("Not Yet Implemented!!!");
                        reader.printAbove("");
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
//666                     PlainNote note = new PlainNote(0, JavaTime.epochSecond(), true, currentChat.senderAddress, currentChat.friendAddress, currentChat.senderGpgKeyId, currentChat.friendGpgKeyId, 0, 0, line);
//666                     bus.post(note);
                }
                prompt = currentChat!=null ? "me" : "lettera";
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
            String yn = lineReader.readLine(prompt+mode.text+": ", null, "").trim();
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
        Set<String> secEmails = getEmails(secs.get());
        Set<String> pubEmails = getEmails(pubs.get());
        Set<String> sessions = getSessions();

        ArrayList<Object> list = new ArrayList<>();

        list.add(_HELP);
        list.add(_ABOUT);
        list.add(_SENDER);
        list.add(_CHAT);
        list.add(_CHAT_RM);
        list.add(_PROXY);
        list.add(_TORIFY);
        list.add(_FRIEND_LS);
        list.add(_FRIEND_RM);

        if(!sessions.isEmpty())
        {
            list.add(node(sessions));
        }
        list.add(_CHAT);
        if(!sessions.isEmpty())
        {
            list.add(node(sessions));
        }

        list.add(_UNREAD);
        list.add(_WAIT_MESSAGE);
        
        list.add(_EXIT);
        
        return new Completers.TreeCompleter(node(list.toArray()));
    }

    private Completer getSecKeysCompleter(String[] keys)
    {
        return new StringsCompleter(keys);
    }

    private static String[] getSecKeyIds(String lookfor)
    {
        ArrayList<String> keys = new ArrayList<>();
        try
        {
            SecKey[] sk = GPG.getSecKeys(lookfor);
            for(MainKey item : sk)
            {
                keys.add(item.getMain().keyid);
            }
        }
        catch (IOException | InterruptedException ex)
        {
            System.getLogger(TerminalChat.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        Collections.sort(keys);
        return keys.toArray(new String[0]);
    }

    private Set<String> getSessions()
    {
        return new HashSet<>();
    }
    
    private Map<Long, PlainNote> chat(String line) throws IOException, InterruptedException
    {
        ansiTitle("/chat");
        
        Args args = new Args(line.split("\\s+"));
        
        String name = args.get(1);
        
        if(Strings.isEmpty(name))
        {
            Set<String> friendNames = getFriendNames();

            name = readLineString("name: ", "", friendNames);
            
            if(name.isEmpty())
            {
                return null;
            }
        }
        
        Friend friend = db.getFriend(name);
        
        if(friend==null || !Emails.isValidEmail(friend.email, true) || !GPG.isValidFingerprint(friend.fingerprint))
        {
            friend = setupFriend(name);
            if(friend==null || !Emails.isValidEmail(friend.email, true))
            {
                return null;
            }
            if(Strings.isNotEmpty(friend.fingerprint) && !GPG.isValidFingerprint(friend.fingerprint))
            {
                return null;
            }
        }

        Set<String> friendFingerprints = getFingerprints(friend.email, pubs.get());
        
        if(Strings.isNotEmpty(friend.fingerprint) && !friendFingerprints.contains(friend.fingerprint))
        {
            System.err.printf("invalid fingerprint (%s) for email (%s)\n", friend.fingerprint, friend.email);
            return null;
        }

        Map<Long,PlainNote> chat = db.openChat(name);

        return chat;
    }
    
    private String readLineEmail(String prompt, String buffer, Iterable<String> emails)
    {
        LineReader lineReader = buildLineReader(new StringsCompleter(emails));
        for(;;)
        {
            String email=lineReader.readLine(prompt, null, buffer!=null ? buffer : "");
            email = email!=null ? email.trim() : email;
            if(Strings.isEmpty(email))
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
    
    private String readLineFingerprint(String prompt, String buffer, Iterable<String> fingerprints)
    {
        LineReader lineReader = buildLineReader(new StringsCompleter(fingerprints));
        for(;;)
        {
            String fp=lineReader.readLine(prompt, null, buffer!=null ? buffer : "");
            fp = fp!=null ? fp.trim() : fp;
            if(Strings.isEmpty(fp) || GPG.isValidFingerprint(fp))
            {
                return fp;
            }
            lineReader.printAbove("not a fingerprint");
        }
    }
    
    private String readLineString(String prompt, String buffer, Iterable<String> tips)
    {
        LineReader lineReader = buildLineReader(tips!=null ? new StringsCompleter(tips) : null);
        try
        {
            for(;;)
            {
                String line=lineReader.readLine(prompt, null, buffer!=null ? buffer : "");
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
        finally
        {
            lineReader.zeroOut();
        }
    }
    
    private String readLineString(String prompt, String buffer)
    {
        return readLineString(prompt, buffer, null);
    }        
    
    private char[] readLinePassword(String prompt)
    {
        if(console)
        {
            return AbstractConsole.getInstance(mock).readPassword("%s", prompt);
        }
        LineReader lineReader = buildLineReader(null);
        try
        {
            return lineReader.readLine(prompt, '*', "").toCharArray();
        }
        finally
        {
            lineReader.zeroOut();
        }
    }

    private char[] readLinePassPath(String prompt)
    {
        String path = readLineString("pass-path:", "").trim();
        return path.isEmpty() ? new char[0] : PASS.getKey(path).toCharArray();
    }    
    
    private int readLinePort(String prompt, int buffer)
    {
        LineReader lineReader = buildLineReader(null);
        try
        {
            for (;;)
            {
                String port = lineReader.readLine(prompt, null, buffer > 0 ? Integer.toString(buffer) : null);
                if (port.isEmpty())
                {
                    return 0;
                }
                port = port.trim();
                int n = Parsers.parseInt(port, 0);
                if (n > 0)
                {
                    return n;
                }
            }
        }
        finally
        {
            lineReader.zeroOut();
        }
    }

    private String readGpgFingerprint(String prompt, String buffer, String[] allowedKeys)
    {
        if(allowedKeys.length==0)
        {
            return "";
        }
        Set<String> allowedSet = As.set(allowedKeys);
        LineReader lineReader = buildLineReader(getSecKeysCompleter(allowedKeys));
        try
        {
            for(;;)
            {
                String fingerprint = lineReader.readLine(prompt, null, buffer).trim();
                if(fingerprint.isEmpty())
                {
                    return fingerprint;
                }
                if(allowedSet.contains(fingerprint))
                {
                    return fingerprint;
                }
                buffer="";
            }
        }
        finally
        {
            lineReader.zeroOut();
        }
    }

    private boolean readBoolean(String prompt, String buffer, String yes, String no, boolean defaultValue)
    {
        LineReader lineReader = buildLineReader(null);
        try
        {
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
        finally
        {
            lineReader.zeroOut();
        }
    }

    boolean existsSecKey(String id) throws IOException, InterruptedException
    {
        if(id.trim().isEmpty())
        {
            return false;
        }
        SecKey[] sec = GPG.getSecKeys(id);
        return sec.length>0;
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
//666                reader.printAbove(currentChat.friendName+"> "+new String(plaintext,UTF8));
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

    //666 @Override
    protected void receive(PlainNote note)
    {
        reader.printAbove(note.text);
    }
    
    private static String normalize(String s)
    {
        return s.replaceAll("[^A-Za-z0-9.,;+-]", "_").replaceAll("__+", "_").toLowerCase();
    }

    private Sender setupSender() throws IOException, InterruptedException
    {
        ansiTitle("/sender");
     
        String email="";
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
        String gpgKeyId="";
        String gpgPassphrase="";

        Sender sender = db.getSender();
                
        if(sender!=null)
        {
            email = sender.email;
            smtpHost = sender.smtpHost;
            smtpPort = sender.smtpPort;
            imapHost = sender.imapHost;
            imapPort = sender.imapPort;
            pop3Host = sender.pop3Host;
            pop3Port = sender.pop3Port;
            auth = sender.auth;
            starttls = sender.starttls;
            username = sender.username;
            gpgKeyId = sender.gpgKeyId;
            gpgPassphrase = sender.gpgPassphrase;
        }
        
        Set<String> secEmails = getEmails(secs.get());
        email = readLineEmail("email: ", email, secEmails);
        if(email.isEmpty())
        {
            return null;
        }
        for(EmailPreset item : EmailProviders.load().values())
        {
            if(item.isDomain(email))
            {
                smtpHost = Strings.firstNonEmpty(smtpHost, item.getSmtp().getHost());
                smtpPort = smtpPort>0 ? smtpPort : item.getSmtp().getPort();
                imapHost = Strings.firstNonEmpty(imapHost, item.getImap().getHost());
                imapPort = imapPort>0 ? imapPort : item.getImap().getPort();
                pop3Host = Strings.firstNonEmpty(pop3Host, item.getPop3().getHost());
                pop3Port = pop3Port>0 ? pop3Port : item.getPop3().getPort();
                username = Strings.firstNonEmpty(username, email, "");
                break;
            }
        }
        
        // SMTP
        smtpHost = readLineString("smtp.host: ", smtpHost);
        if(smtpHost.isEmpty())
        {
            return null;
        }
        smtpPort = readLinePort("smtp.port: ", smtpPort);
        if(smtpPort==0)
        {
            return null;
        }
        
        // IMAP
        imapHost = readLineString("imap.host: ", imapHost);
        if(imapHost.isEmpty())
        {
            return null;
        }
        imapPort = readLinePort("imap.port: ", imapPort);
        if(imapPort==0)
        {
            return null;
        }
        
        // POP3
//        pop3Host = readString("pop3.host: ", pop3Host);
//        if(pop3Host.isEmpty())
//        {
//            return null;
//        }
//        pop3Port = readPort("pop3.port: ", pop3Port);
//        if(pop3Port==0)
//        {
//            return null;
//        }
        
        auth = readBoolean("auth[Y/n]: ", "","y", "n", auth);
        starttls = readBoolean("starttls[Y/n]: ", "","y", "n", starttls);
        
        username = readLineString("username: ",username);
        if(username.isEmpty())
        {
            return null;
        }

        char[] password = passpath ? readLinePassPath("password-path: ") : readLinePassword("password: ");
        if(password.length==0 && sender!=null)
        {
            emailPass=sender.emailPass;
        }
        else
        {
            emailPass=keyWrapper.wrapKey("email", "pass", password);
        }

        String[] keyIds = getSecKeyIds(email);
        gpgKeyId = readGpgFingerprint("key: ", gpgKeyId, keyIds);
        if(gpgKeyId.isEmpty() && keyIds.length>0)
        {
            gpgKeyId = keyIds[0];
            gpgKeyId = readGpgFingerprint("key: ", gpgKeyId, keyIds);
        }
        
        if(gpgKeyId.isEmpty())
        {
            char[] passphrase = passphraser.chars("gpg+passphrase");
            int rc = GPG.genKey(CURVE25519, CA, CURVE25519, E, username, "lettera", email, new String(passphrase), "4y");
            if(rc==0)
            {
                SecKey[] keys = GPG.getSecKeys(email);
                gpgKeyId = keys[0].getMain().getFingerprint();
                rc = GPG.addKeyECC(gpgKeyId, true, false, false, 1, "4y", new String(passphrase));
            }
            if(rc!=0)
            {
                System.out.printf("error: %d\n",rc);
                gpgKeyId = "";
                gpgPassphrase = "";
            }
            else
            {
                gpgPassphrase = keyWrapper.wrapKey("gpg","pass", passphrase);
            }        
        }
        else
        {
            char[] passphrase = passpath ? readLinePassPath("passphrase-path: ") : readLinePassword("passphrase: ");
            if(passphrase.length==0)
            {
                passphrase = passphraser.chars("gpg+passphrase");
            }
            gpgPassphrase = keyWrapper.wrapKey("gpg","pass", passphrase);
        }
        sender = new Sender(email, auth, starttls, smtpHost, smtpPort, imapHost, imapPort, pop3Host, pop3Port, username, emailPass, gpgKeyId, gpgPassphrase, "");
        
        this.db.putSender(sender);
        this.db.commit();
       
        return sender;        
    }    
    
    private Friend setupFriend(String name)
    {
        ansiTitle("/friend");
        
        name = normalize(name);
        
        if(Strings.isEmpty(name))
        {
            name = readLineString("name: ", "");
            name = normalize(name);
            if(name.isEmpty())
            {
                return null;
            }
        }
        
        String email = null;
        String fingerprint = null;
        String sharedSecret = null;
        String sessionKey = null;
        int trustLevel = 0;

        Friend friend = db.getFriend(name);
        if(friend!=null)
        {
            email = friend.email;
            fingerprint = friend.fingerprint;
            sharedSecret = friend.sharedSecret;
            sessionKey = friend.sessionKey;
            trustLevel = friend.trustLevel;
        }
        
        Set<String> pubEmails = getEmails(pubs.get());
        
        email = readLineEmail("email: ", email, pubEmails);
        if(email.isEmpty())
        {
            return null;
        }

        Set<String> pubFingerprints = getFingerprints(email, pubs.get());
        fingerprint = readLineFingerprint("fingerprint: ", fingerprint, pubFingerprints);

        sharedSecret = readLineString("shared-secret: ", sharedSecret);
        
        friend = new Friend(name, email, fingerprint, sharedSecret, sessionKey, trustLevel);
        
        this.db.putFriend(friend);
        this.db.commit();
        return friend;
    }    
}
