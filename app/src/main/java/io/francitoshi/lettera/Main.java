/*
 * Copyright (C) 2025-2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.lettera;

import io.francitoshi.lettera.Lettera.Mode;
import io.nut.base.crypto.gpg.PASS;
import io.nut.base.encoding.Base64DecoderException;
import io.nut.base.io.ThrottledInputStream;
import io.nut.base.logging.Log;
import io.nut.base.net.HostPort;
import io.nut.base.net.Socks5;
import io.nut.base.net.Tor;
import io.nut.base.net.Tor.SocksPolicy;
import io.nut.base.options.BooleanOption;
import io.nut.base.options.CommandOption;
import io.nut.base.options.MissingOptionParameterException;
import io.nut.base.options.OptionParser;
import io.nut.base.options.StringOption;
import io.nut.base.platform.Snap;
import io.nut.base.i18n.I18n;
import io.nut.base.security.SecureChars;
import io.nut.base.lang.Java;
import io.nut.base.util.Utils;
import io.nut.base.concurrent.actor.ActorHub;
import static io.nut.base.concurrent.actor.ActorPool.CORES;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import org.jline.terminal.*;

public class Main
{
    static final String LETTERA = "lettera";
    static final String COPYRIGHT = "Copyright (C) 2025-2026 francitoshi@gmail.com";
    static final String VER = Utils.firstNonNull(Main.class.getPackage().getImplementationVersion(), "[dev]");
    private static final String VERSION = LETTERA + " v" + VER;
    static final String LICENSE_TXT;
    static final String HELP_TXT;
    static final String WELCOME_TXT;

    static final String LETTERA_TXT;
    
    static final int    LOG_SIZE = 128*1024;
    static final int    LOG_COUNT = 9;

    static final String TOR_HOST = "127.0.0.1";
    static final int    TOR_PORT = 9050;
    static final String TOR_HOST_PORT = TOR_HOST+":"+TOR_PORT;

    static final String GMAIL_ADD_PASS = "https://myaccount.google.com/apppasswords";

    static 
    {
        I18n i18n = I18n.of(Main.class);

        HELP_TXT = i18n.resolveResource("help", "");
        LICENSE_TXT = i18n.resolveResource("license", "").replace("$COPYRIGHT$", COPYRIGHT);
        LETTERA_TXT = i18n.getResource("lettera.txt", "LETTERA");

        WELCOME_TXT = i18n.resolveResource("welcome", "")
                .replace("$LETTERA$", LETTERA_TXT)
                .replace("$VERSION$", VER)
                .replace("$COPYRIGHT$", COPYRIGHT);
    }
            
    private static volatile Log log;
        
    public static void main(String... args) throws Exception
    {
//        NewProjectWizard.main(args);
//        MenuExample.main(args);
        OptionParser options = new OptionParser();
        
        CommandOption sendCmd = options.add(new CommandOption('s',"send"));
        CommandOption senderCmd = options.add(new CommandOption("sender"));
        CommandOption friendsCmd = options.add(new CommandOption("friends"));
        
        StringOption dirOp = options.add(new StringOption('d', "dir"));
        StringOption usernameOp = options.add(new StringOption('u', "username"));
        StringOption passphraseOp = options.add(new StringOption('p', "passphrase"));
        BooleanOption passPathOp = options.add(new BooleanOption('P', "pass-path"));
        StringOption inputOp = options.add(new StringOption('I', "input"));
        StringOption outputOp = options.add(new StringOption('O', "output"));
        BooleanOption noWizardOp = options.add(new BooleanOption('W', "no-wizard"));
        BooleanOption license = options.add(new BooleanOption('L', "license"));
        BooleanOption verboseOp = options.add(new BooleanOption('v', "verbose"));
        StringOption proxyOp = options.add(new StringOption('X', "proxy"));
        StringOption torOp = options.add(new StringOption('T', "tor"));
        BooleanOption version = options.add(new BooleanOption("version"));
        BooleanOption help = options.add(new BooleanOption('h', "help"));
        BooleanOption snapOp = options.add(new BooleanOption('S', "snap"));
        BooleanOption lite = options.add(new BooleanOption("lite"));
        
        try
        {
            args = options.parse(args);

            if (help.isUsed())
            {
                System.out.println(HELP_TXT);
                return;
            }
            if (version.isUsed())
            {
                System.out.println(VERSION);
                return;
            }
            if (license.isUsed())
            {
                System.out.println(LICENSE_TXT);
                return;
            }            
            
            boolean cmdUsed = CommandOption.isUsed(sendCmd, senderCmd, friendsCmd);

            if(!cmdUsed)
            {
                System.out.println(Main.WELCOME_TXT);
            }
            
            final File letteraDir;
            if (dirOp.isUsed())
            {
                letteraDir = new File(dirOp.getValue()).getCanonicalFile();
                if(letteraDir.exists() && letteraDir.isFile())
                {
                    System.err.println("'%s' is an existing file");
                    System.exit(1);
                }
                System.out.printf("data-path: %s\n", letteraDir);
            }
            else if(Snap.isSnap() && snapOp.isUsed())
            {
                letteraDir = Snap.fixTmpDir();
            }
            else
            {
                letteraDir = new File(Java.USER_HOME, ".lettera");
            }            

            setupLoggers(verboseOp.getCount(), letteraDir);
           
            InputStream input = null;
            
            String username = null;
            if (usernameOp.isUsed())
            {
                username = usernameOp.getValue();
                log.info("username: %s", username);
            }

            SecureChars passphrase = null;
            if (passphraseOp.isUsed())
            {
                if(passPathOp.isUsed())
                {
                    passphrase = new SecureChars(PASS.getKey(passphraseOp.getValue()).toCharArray());
                }
                else
                {
                    passphrase = new SecureChars(passphraseOp.getValue().toCharArray());
                }
            }
        
            if (inputOp.isUsed())
            {
                File file = new File(inputOp.getValue());
                if (!file.exists())
                {
                    System.err.printf("can't find %s'\n", file);
                    System.exit(1);
                }
                input = new ThrottledInputStream(new FileInputStream(file), 66, 200, 60_000, TimeUnit.MILLISECONDS, false).setSingleLine(true);
            }

            OutputStream output = System.out;

            if (outputOp.isUsed())
            {
                output = new FileOutputStream(outputOp.getValue());
            }

            boolean wizard = !noWizardOp.isUsed();
            boolean mock = input!=null || System.console()==null;
            boolean passpath = passPathOp.isUsed();
            if(proxyOp.isUsed() && torOp.isUsed())
            {
                System.err.println("can't use --proxy and --tor at the same time");
                System.exit(1);
            }

            if(proxyOp.isUsed())
            {
                HostPort hostPort = proxyOp.isUsed() ? getHostPort(proxyOp, TOR_HOST_PORT) : null;
                Socks5 socks5 = new Socks5(hostPort.host, hostPort.port);
                socks5.installGlobally();
            }
            else if(torOp.isUsed())
            {
                HostPort hostPort = torOp.isUsed() ? getHostPort(proxyOp, TOR_HOST_PORT) : null;
                Tor tor = Tor.managed(hostPort.port, SocksPolicy.LOCALHOST_ONLY);
                tor.installGlobally();
            }
                     
            final ActorHub hive = new ActorHub(ActorHub.CORES, ActorHub.CORES, 30_000, true);
    
            if(cmdUsed)
            {
                try(Lettera lettera = new Lettera(hive, System.out, letteraDir, username, passphrase, passpath, mock, verboseOp.isUsed()).open())
                {
                    if(sendCmd.isUsed())
                    {
                        lettera.startChat(args[0], Mode.Write);
                        for(int i=1;i<args.length;i++)
                        {
                            lettera.send(args[i]);
                        }
                    }
                    else if(friendsCmd.isUsed())
                    {
                        lettera.listFriends();
                    }
//                lettera.setWizard(wizard);
//                lettera.send();
                }
            }
            else
            {
                try (Terminal terminal = getTerminal(mock, input, output))
                {
                    try(TerminalChat chat = new TerminalChat(hive, terminal, letteraDir, username, passphrase, passpath, mock, verboseOp.isUsed()).open())
                    {
                        chat.setWizard(wizard);
                        if(lite.isUsed())
                        {
                            System.err.println("NOT YET IMPLEMENTED");
                        }
                        else
                        {
                            chat.run();
                        }
                    }
                }
            }
        }
        catch (NoSuchAlgorithmException | CertificateException | KeyStoreException | MissingOptionParameterException | IOException | Base64DecoderException ex)
        {
            System.getLogger(Main.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        catch (Exception ex)
        {
            System.getLogger(Main.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
    }

    static void setupLoggers(int verboseCount, File letteraDir) throws IOException
    {
        verboseCount = Math.min(verboseCount, Log.WARN);
        int consoleLevel = Log.WARN-verboseCount;
        int fileLevel    = Math.max(consoleLevel-1, 0);
        
        File logDir = new File(letteraDir,"log");
        logDir.mkdirs();
        String pattern = logDir.getPath()+"/lettera.log.%g";
        ConsoleHandler ch = Log.getConsoleHandler(consoleLevel, Log.FormatType.DT_LEV_MSG);
        FileHandler fh = Log.getFileHandler(fileLevel, Log.FormatType.DTZ_LEV_NAME_MSG, pattern, LOG_SIZE, LOG_COUNT, false);
        Log.setJulBuilder(false, consoleLevel, ch, fh);
        log = Log.of(Main.class);
    }

    public static HostPort getHostPort(StringOption option, String defaultValue) throws NumberFormatException
    {
        String[] hostPort = option.getValue(defaultValue).split("[:]");
        if(hostPort.length>1)
        {
            return new HostPort((hostPort[0]), Integer.parseInt(hostPort[1]));
        }
        else if(hostPort.length>0)
        {
            return new HostPort(hostPort[0], TOR_PORT);
        }
        return new HostPort(TOR_HOST, TOR_PORT);
    }
    
    private static Terminal getTerminal(boolean mock, InputStream input, OutputStream output) throws IOException
    {
        if(mock)
        {
            return new MockTerminal(input, output);
        }
        return TerminalBuilder.builder().build();
    }

}
