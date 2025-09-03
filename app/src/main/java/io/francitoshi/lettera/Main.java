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
import io.nut.base.io.ThrottledInputStream;
import io.nut.base.options.BooleanOption;
import io.nut.base.options.OptionParser;
import io.nut.base.options.StringOption;
import io.nut.base.security.SecureChars;
import io.nut.base.util.Java;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.*;

public class Main
{

    private static final String LETTERA = "lettera";
    private static final String VER = "0.0.1";
    private static final String VERSION
            = LETTERA + "  version " + VER + " (2025-08-27)\n"
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
            + " -L, --license               display software license\n"
            + " -p, --passphrase[=pass]     set the master password\n"
            + " -P, --pass-pass[=path]      set the master password usign command 'pass path'\n"
            + " -I, --input[=path]          set a text file as stdin'\n"
            + " -W, --no-wizard             disable wizards'\n"
            + "     --debug                 debug mode\n"
            + "     --version               print version number\n"
            + "(-h) --help                  show this help (-h works with no other options)\n"
            + "\n"
            + REPORT_BUGS;

    static final String LETTERA_DB = "lettera.db";
    static final String GMAIL_ADD_PASS = "https://myaccount.google.com/apppasswords";

    public static void main(String... args) throws Exception
    {
//        String password = PASS.getKey("mutt/flikxxi@gmail.com");
//        
//        boolean imap = true;
//        int port = imap ? IMAP.SAFE_PORT_993 : POP3.SAFE_PORT_995;
//        MailBot mailBot = new MailBot(imap, "pop.gmail.com", port, true, true, "flikxxi@gmail.com", password);
//        mailBot.start();

        OptionParser options = new OptionParser();
        StringOption passphraseOp = options.add(new StringOption('p', "passphrase"));
        StringOption passPassOp = options.add(new StringOption('P', "pass-pass"));
        StringOption inputOp = options.add(new StringOption('I', "input"));
        StringOption noWizardOp = options.add(new StringOption('W', "no-wizard"));
        BooleanOption license = options.add(new BooleanOption('L', "license"));
        BooleanOption version = options.add(new BooleanOption("version"));
        BooleanOption debug = options.add(new BooleanOption("debug"));
        BooleanOption help = options.add(new BooleanOption('h', "help"));

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

        final File letteraDir = new File(Java.USER_HOME, ".lettera");
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
            input = new ThrottledInputStream(new FileInputStream(inputOp.getValue()), 100, 400, TimeUnit.MILLISECONDS, true);
        }
        boolean wizard = !noWizardOp.isUsed();

        TerminalBuilder builder = TerminalBuilder.builder();

        builder = input != null ? builder.streams(input, System.out).system(false).dumb(true) : builder;

        try (Terminal terminal = builder.build())
        {
            TerminalChat chat = new TerminalChat(terminal, configFile, keystoreFile, letteraDb, passphrase, debug.isUsed());
            chat.setWizard(wizard);
            chat.run();
        }
    }
}
