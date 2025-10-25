/*
 *  MainTest.java
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

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import io.nut.base.crypto.Kripto;
import io.nut.base.crypto.Rand;
import io.nut.base.crypto.gpg.GPG;
import static io.nut.base.crypto.gpg.GPG.NISTP521;
import io.nut.base.crypto.gpg.SecKey;
import io.nut.base.util.concurrent.hive.Hive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 *
 * @author franci
 */
public class MainTest
{
    static final Rand RAND = Kripto.getRand();
    
    static final String PASSPHRASE = "eureka";
    
    static final String ALICE = "alice";
    static final String BOB = "bob";
    
    static final String ALICE_PASSWORD = "alice-email-password";
    static final String BOB_PASSWORD = "bob-email-password";

    static final String ALICE_PASSPHRASE = "alice-gpg-passphrase";
    static final String BOB_PASSPHRASE = "bob-gpg-passphrase";

    static final String ALICE_LOCALHOST = "alice@localhost";
    static final String BOB_LOCALHOST = "bob@localhost";
    
    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP_POP3_IMAP)
            .withConfiguration(GreenMailConfiguration.aConfig()
            .withUser(ALICE_LOCALHOST, ALICE, ALICE_PASSWORD)
            .withUser(BOB_LOCALHOST, BOB, BOB_PASSWORD));
    
    public MainTest()
    {
    }
    
    final GPG gpg = new GPG().setDebug(true);

    @BeforeEach
    public void setUp() throws Exception
    {
        if(gpg.getSecKeys(ALICE).length==0)
        {
            gpg.genKey(NISTP521, GPG.SCA, NISTP521, GPG.E, ALICE, "", ALICE_LOCALHOST, ALICE_PASSPHRASE, "4y");
        }
        if(gpg.getSecKeys(BOB).length==0)
        {
            gpg.genKey(NISTP521, GPG.SCA, NISTP521, GPG.E, BOB, "", BOB_LOCALHOST, BOB_PASSPHRASE, "4y");
        }
    }

    @AfterEach
    public void tearDown() throws Exception
    {
        SecKey[] alice = gpg.getSecKeys(ALICE_LOCALHOST);
        for(SecKey key : alice)
        {
            gpg.deleteSecAndPubKeys(key.getMain().getFingerprint());
        }
        SecKey[] bob = gpg.getSecKeys(BOB_LOCALHOST);
        for(SecKey key : bob)
        {
            gpg.deleteSecAndPubKeys(key.getMain().getFingerprint());
        }
    }

    @Test
    public void testMain1() throws Exception
    {
        Main.main("--passphrase", PASSPHRASE, "--input", "test/input-test-alice1.txt", "--no-wizard", "--debug", "-d","./tmp/test-alice1");
        Main.main("--passphrase", PASSPHRASE, "--input", "test/input-test-bob1.txt", "--no-wizard", "--debug", "-d","./tmp/test-bob1");
    }
    
    @Test
    public void testMain2() throws Exception
    {
        Hive hive = new Hive(2);
        hive.execute( () -> Main.main("--passphrase", PASSPHRASE, "--input", "test/input-test-alice2.txt", "--no-wizard", "--debug", "-d","./tmp/test-alice2"));
        Main.main("--passphrase", PASSPHRASE, "--input", "test/input-test-bob2.txt", "--no-wizard", "--debug", "-d","./tmp/test-bob2");
    }
}
