/*
 * Copyright (C) 2025-2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.lettera;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import static io.francitoshi.lettera.Lettera.GPG;
import io.nut.base.commons.concurrent.actor.ActorHub;
import io.nut.base.commons.io.IO;
import io.nut.base.commons.jca.Rand;
import io.nut.base.crypto.gpg.GPG;
import io.nut.base.crypto.Kripto;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 *
 * @author franci
 */
public class MainTest
{
    static final Rand RAND = Rand.getThreadLocalInstance();
    
    static final String PASSPHRASE = "eureka";
    
    static final String ALICE = "alice";
    static final String ALICE_LOCALHOST = "alice@localhost";
    static final String ALICE_LETTERA_PASSPHRASE = "alice-lettera-passphrase";
    static final String ALICE_EMAIL_PASS = "alice-email-pass";
    static final String ALICE_GPG_PASSPHRASE = "alice-gpg-passphrase";
    
    static final String BOB = "bob";
    static final String BOB_LOCALHOST = "bob@localhost";
    static final String BOB_LETTERA_PASS = "bob-lettera-passphrase";
    static final String BOB_EMAIL_PASS = "bob-email-pass";
    static final String BOB_GPG_PASSPHRASE = "bob-gpg-passphrase";
    
    public static final String TMP_TEST_ALICE = "./tmp/test-alice";
    
    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP_POP3_IMAP)
            .withConfiguration(GreenMailConfiguration.aConfig()
            .withUser(ALICE_LOCALHOST, ALICE, ALICE_EMAIL_PASS)
            .withUser(BOB_LOCALHOST, BOB, BOB_EMAIL_PASS));
    
    public MainTest()
    {
    }
    
    final GPG gpg = new GPG().setDebug(false);

    public void createAliceBobGpg() throws InterruptedException, IOException
    {
        if(gpg.getSecKeys(ALICE_LOCALHOST).length==0)
        {
            gpg.genKey(GPG.CURVE25519, GPG.SCA, GPG.CURVE25519, GPG.E, ALICE, "", ALICE_LOCALHOST, ALICE_GPG_PASSPHRASE.toCharArray(), "4y");
        }
        if(gpg.getSecKeys(BOB_LOCALHOST).length==0)
        {
            gpg.genKey(GPG.RSA4096, GPG.SCA, GPG.RSA4096, GPG.E, BOB, "", BOB_LOCALHOST, BOB_GPG_PASSPHRASE.toCharArray(), "4y");
        }
    }
    
    @BeforeEach
    public void setUp() throws Exception
    {
        IO.delete(new File(TMP_TEST_ALICE), true);
    }

    
    //@AfterEach
    public void tearDown() throws Exception
    {
//        SecKey[] alice = gpg.getSecKeys(ALICE_LOCALHOST);
//        for(SecKey key : alice)
//        {
//            gpg.deleteSecAndPubKeys(key.getMain().getFingerprint());
//        }
//        SecKey[] bob = gpg.getSecKeys(BOB_LOCALHOST);
//        for(SecKey key : bob)
//        {
//            gpg.deleteSecAndPubKeys(key.getMain().getFingerprint());
//        }
//        IO.delete(new File(TMP_TEST_ALICE), true);
    }


    @Test
    //@Disabled
    public void testMain1() throws Exception
    {
        createAliceBobGpg();
        Main.main("--passphrase", PASSPHRASE, "-u", ALICE, "-p", ALICE_LETTERA_PASSPHRASE, "--input", "test/01_alice_input.txt", "-vv", "-d", TMP_TEST_ALICE);
//666        Main.main("--passphrase", PASSPHRASE, "--input", "test/input-test-bob1.txt", "--no-wizard", "--debug", "-d","./tmp/test-bob1");
    }

    
    @Test
    
    public void testMain2() throws Exception
    {        
        ActorHub hive = new ActorHub(2);
//666        hive.execute( () -> Main.main("--passphrase", PASSPHRASE, "--input", "test/input-test-alice2.txt", "--no-wizard", "--debug", "-d","./tmp/test-alice2"));
//666        Main.main("--passphrase", PASSPHRASE, "--input", "test/input-test-bob2.txt", "--no-wizard", "--debug", "-d","./tmp/test-bob2");
    }
}
