/*
 *  Main.java
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

import io.nut.base.crypto.gpg.PASS;
import io.nut.base.encoding.Base64DecoderException;
import io.nut.base.io.ThrottledInputStream;
import io.nut.base.options.BooleanOption;
import io.nut.base.options.MissingOptionParameterException;
import io.nut.base.options.OptionParser;
import io.nut.base.options.StringOption;
import io.nut.base.security.SecureChars;
import io.nut.base.util.Java;
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
import org.jline.terminal.*;

public class Main
{

    private static final String LETTERA = "lettera";
    private static final String VER = "0.0.1";
    private static final String VERSION
            = LETTERA + "  version " + VER + " (2025-10-25)\n"
            + "Copyright (C) 2025 by francitoshi@gmail.com\n";
    private static final String REPORT_BUGS
            = "Report bugs to <francitoshi@gmail.com>\n";
    private static final String LICENSE
            = VERSION
            + "\n"
            + "This program is free software: you can redistribute it and/or modify\n"
            + "it under the terms of the GNU General Public License as published by\n"
            + "the Free Software Foundation, either version 3 of the License, or\n"
            + "(at your option) any later version.\n"
            + "\n"
            + "This program is distributed in the hope that it will be useful,\n"
            + "but WITHOUT ANY WARRANTY; without even the implied warranty of\n"
            + "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the\n"
            + "GNU General Public License for more details.\n"
            + "\n"
            + "You should have received a copy of the GNU General Public License\n"
            + "along with this program.  If not, see <http://www.gnu.org/licenses/>.\n"
            + "\n"
            + REPORT_BUGS;
    private static final String HELP
            = VERSION
            + "\n"
            + "lettera comes with ABSOLUTELY NO WARRANTY. This is free software, and you\n"
            + "are welcome to redistribute it under certain conditions. See the GNU\n"
            + "General Public Licence version 3 for details.\n"
            + "\n"
            + "lettera let you chat with privacy.\n"
            + "\n"
            + "usage: lettera [options]\n"
            + "\n"
            + "Options:\n"
            + " -d  --dir                   path to lettera home folder (~/.lettera)\n"
            + " -L, --license               display software license\n"
            + " -p, --passphrase[=pass]     set the master password\n"
            + " -P, --pass-pass[=path]      set the master password usign command 'pass path'\n"
            + " -I, --input[=path]          set a text file as stdin'\n"
            + " -O, --output[=path]         set a text file as stdout'\n"
            + " -W, --no-wizard             disable wizards'\n"
            + "     --debug                 debug mode\n"
            + "     --version               print version number\n"
            + "(-h) --help                  show this help (-h works with no other options)\n"
            + "\n"
            + REPORT_BUGS;

    static final String LETTERA_DB = "lettera.db";
    static final String GMAIL_ADD_PASS = "https://myaccount.google.com/apppasswords";

    public static void main(String... args)
    {
//        String password = PASS.getKey("mutt/flikxxi@gmail.com");
//        
//        boolean imap = true;
//        int port = imap ? IMAP.SAFE_PORT_993 : POP3.SAFE_PORT_995;
//        MailBot mailBot = new MailBot(imap, "pop.gmail.com", port, true, true, "flikxxi@gmail.com", password);
//        mailBot.start();

        OptionParser options = new OptionParser();
        StringOption dirOp = options.add(new StringOption('d', "dir"));
        StringOption passphraseOp = options.add(new StringOption('p', "passphrase"));
        StringOption passPassOp = options.add(new StringOption('P', "pass-pass"));
        StringOption inputOp = options.add(new StringOption('I', "input"));
        StringOption outputOp = options.add(new StringOption('O', "output"));
        BooleanOption noWizardOp = options.add(new BooleanOption('W', "no-wizard"));
        BooleanOption license = options.add(new BooleanOption('L', "license"));
        BooleanOption debugOp = options.add(new BooleanOption('D', "debug"));
        BooleanOption version = options.add(new BooleanOption("version"));
        BooleanOption help = options.add(new BooleanOption('h', "help"));

        if(Snap.isSnap())
        {
            Snap.fixTmpDir();
        }
        System.out.println("..."+getHomeDir());   
        
        
        try
        {
            args = options.parse(args);

            if (help.isUsed())
            {
                System.out.println(HELP);
                return;
            }
            if (version.isUsed())
            {
                System.out.println(VERSION);
                return;
            }
            if (license.isUsed())
            {
                System.out.println(LICENSE);
                return;
            }
            System.out.println(TerminalChat.WELCOME);

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
            
            final File letteraDir = altDir!=null ? altDir : new File(getHomeDir(), ".lettera");
            final File configFile = new File(letteraDir, "config.properties");
            final File keystoreFile = new File(letteraDir, "keystore.p12");
            final File letteraDb = new File(letteraDir, LETTERA_DB);

            System.out.println("letteraDir="+letteraDir);
            System.out.println("mkdirs()="+letteraDir.mkdirs());
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
                input = new ThrottledInputStream(new FileInputStream(file), 66, 200, 60_000, TimeUnit.MILLISECONDS, false);
            }

            OutputStream output = System.out;

            if (outputOp.isUsed())
            {
                output = new FileOutputStream(outputOp.getValue());
            }

            boolean wizard = !noWizardOp.isUsed();
            boolean mock = input!=null;

            try (Terminal terminal = getTerminal(mock, input, output))
            {
                TerminalChat chat = new TerminalChat(terminal, configFile, keystoreFile, letteraDb, passphrase, mock, debugOp.isUsed());
                chat.setWizard(wizard);
                chat.run();
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
    
    private static Terminal getTerminal(boolean mock, InputStream input, OutputStream output) throws IOException
    {
        if(mock)
        {
            return new MockTerminal(input, output);
        }
        return TerminalBuilder.builder().build();
    }

    private static String getHomeDir()
    {
        return Snap.isSnap() ? Snap.SNAP_USER_DATA : Java.USER_HOME;
    }
}
