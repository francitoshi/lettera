/*
 *  Main.java
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


import io.francitoshi.lettera.Lettera.Mode;
import io.nut.base.crypto.gpg.PASS;
import io.nut.base.encoding.Base64DecoderException;
import io.nut.base.io.ThrottledInputStream;
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
import io.nut.base.resources.ResourceBundles;
import io.nut.base.security.SecureChars;
import io.nut.base.util.Java;
import io.nut.base.util.Utils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;
import org.jline.terminal.*;

public class Main
{
    private static final String LETTERA = "lettera";
    private static final String REPORT_BUGS = "Report bugs to <francitoshi@gmail.com>\n";

    static final String LETTERA_DB = "lettera.db";
    static final String GMAIL_ADD_PASS = "https://myaccount.google.com/apppasswords";
    static final String TOR_HOST = "127.0.0.1";
    static final int    TOR_PORT = 9050;
    static final String TOR_HOST_PORT = TOR_HOST+":"+TOR_PORT;

    static final String VER = Utils.firstNonNull(Main.class.getPackage().getImplementationVersion(), "dev");
    static final String LICENSE_TXT;
    static final String MAIN_HELP_TXT;
    static final String WELCOME_TXT;
    static final String LETTERA_TXT;

    static 
    {
        ResourceBundle bundle = ResourceBundle.getBundle(Main.class.getName(), Locale.getDefault());

        MAIN_HELP_TXT = ResourceBundles.getResourceAsString(Main.class, "main_help.txt", "");
        LICENSE_TXT = ResourceBundles.getResourceAsString(Main.class, "license.txt", "");
        LETTERA_TXT = ResourceBundles.getResourceAsString(Main.class, "lettera.txt", "LETTERA");

        String welcomeFileName = bundle.getString("welcome");
        WELCOME_TXT = ResourceBundles.getResourceAsString(Main.class, welcomeFileName, "welcome").replace("$LETTERA$", LETTERA_TXT).replace("$VERSION$", VER);
    }
    
    private static final String VERSION 
            = LETTERA + "  version " + VER + " (2026-03-27)\n"
            + "Copyright (C) 2025-2026 by francitoshi@gmail.com\n";
    private static final String VERSION_LICENSE_REPORT_BUGS = VERSION + "\n\n" + LICENSE_TXT + "\n\n" + REPORT_BUGS;
    private static final String VERSION_HELP_REPORT_BUGS = VERSION + "\n\n" + MAIN_HELP_TXT + "\n\n" + REPORT_BUGS;
    
    public static void main(String... args)
    {
        OptionParser options = new OptionParser();
        
        CommandOption sendCmd = options.add(new CommandOption('s',"send"));
        CommandOption setupAccountCmd = options.add(new CommandOption("setup-account"));
        CommandOption listAccountsCmd = options.add(new CommandOption("list-accounts"));
        CommandOption listFriendsCmd = options.add(new CommandOption("list-friends"));
        CommandOption listChatsCmd = options.add(new CommandOption("list-chats"));
        
        StringOption dirOp = options.add(new StringOption('d', "dir"));
        StringOption passphraseOp = options.add(new StringOption('p', "passphrase"));
        StringOption passPassOp = options.add(new StringOption('P', "pass-pass"));
        StringOption inputOp = options.add(new StringOption('I', "input"));
        StringOption outputOp = options.add(new StringOption('O', "output"));
        BooleanOption noWizardOp = options.add(new BooleanOption('W', "no-wizard"));
        BooleanOption license = options.add(new BooleanOption('L', "license"));
        BooleanOption debugOp = options.add(new BooleanOption('D', "debug"));
        StringOption proxyOp = options.add(new StringOption('X', "proxy"));
        StringOption torOp = options.add(new StringOption('T', "tor"));
        BooleanOption version = options.add(new BooleanOption("version"));
        BooleanOption help = options.add(new BooleanOption('h', "help"));
        BooleanOption noSnapOp = options.add(new BooleanOption('S', "no-snap"));
        
        try
        {
            args = options.parse(args);

            if (help.isUsed())
            {
                System.out.println(VERSION_HELP_REPORT_BUGS);
                return;
            }
            if (version.isUsed())
            {
                System.out.println(VERSION);
                return;
            }
            if (license.isUsed())
            {
                System.out.println(VERSION_LICENSE_REPORT_BUGS);
                return;
            }
            boolean cmdUsed = CommandOption.isUsed(sendCmd, listAccountsCmd, listFriendsCmd, listChatsCmd);

            if(!cmdUsed)
            {
                System.out.println(Main.WELCOME_TXT);
            }

            SecureChars passphrase = null;
            if (passphraseOp.isUsed())
            {
                passphrase = new SecureChars(passphraseOp.getValue().toCharArray());
                System.out.println("passphrase: ****************");
            }
            if (passPassOp.isUsed())
            {
                passphrase = new SecureChars(PASS.getKey(passPassOp.getValue()).toCharArray());
                System.out.println("pass-pass: ****************");
            }
            File altDir = null;
            if (dirOp.isUsed())
            {
                altDir = new File(dirOp.getValue()).getCanonicalFile();
                if(altDir.exists() && altDir.isFile())
                {
                    System.err.println("'%s' is an existing file");
                    System.exit(1);
                }
                System.out.printf("data-path: %s\n", altDir);
            }
            else if(Snap.isSnap() && !noSnapOp.isUsed())
            {
                altDir = Snap.fixTmpDir();
            }
            
            final File letteraDir = altDir!=null ? altDir : new File(Java.USER_HOME, ".lettera");
            final File configFile = new File(letteraDir, "config.properties");
            final File keystoreFile = new File(letteraDir, "keystore.p12");
            final File letteraDb = new File(letteraDir, LETTERA_DB);

            letteraDir.mkdirs();
        
            InputStream input = null;

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
            
            if(cmdUsed)
            {
                try(Lettera lettera = new Lettera(System.out, configFile, keystoreFile, letteraDb, passphrase, mock, debugOp.isUsed()).open())
                {
                    if(sendCmd.isUsed())
                    {
                        lettera.startChat(args[0], Mode.Write);
                        for(int i=1;i<args.length;i++)
                        {
                            lettera.send(args[i]);
                        }
                    }
                    else if(listAccountsCmd.isUsed())
                    {
                        lettera.listAccounts();
                    }
                    else if(listFriendsCmd.isUsed())
                    {
                        lettera.listFriends();
                    }
                    else if(listChatsCmd.isUsed())
                    {
                        lettera.listChats();
                    }
//                lettera.setWizard(wizard);
//                lettera.send();
                }
            }
            else
            {
                try (Terminal terminal = getTerminal(mock, input, output))
                {
                    try(TerminalChat chat = new TerminalChat(terminal, configFile, keystoreFile, letteraDb, passphrase, mock, debugOp.isUsed()).open())
                    {
                        chat.setWizard(wizard);
                        chat.run();
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
