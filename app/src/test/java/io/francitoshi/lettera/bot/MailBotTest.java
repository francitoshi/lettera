/*
 *  MainTest.java
 *
 *  Copyright (c) 2026 francitoshi@gmail.com
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
package io.francitoshi.lettera.bot;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import io.francitoshi.lettera.data.SealedNote;
import io.francitoshi.lettera.email.EmailPreset;
import io.francitoshi.lettera.email.EmailProviders;
import io.francitoshi.lettera.email.ServerSettings;
import io.nut.base.crypto.Kripto;
import io.nut.base.crypto.Rand;
import io.nut.base.crypto.SKIP;
import io.nut.base.crypto.gpg.GPG;
import static io.nut.base.crypto.gpg.GPG.RSA1024;
import io.nut.base.crypto.gpg.PASS;
import io.nut.base.time.JavaTime;
import io.nut.base.concurrent.actor.Actor;
import io.nut.base.concurrent.actor.ActorHub;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 *
 * @author franci
 */
public class MailBotTest
{
    static final Rand RAND = Kripto.getRand();
    
    static final String ALICE = "alice";
    static final String BOB = "bob";
    
    static final String ALICE_PASS = "alice-pass"+RAND.nextLong();
    static final String BOB_PASS = "bob-pass"+RAND.nextLong();

    static final String ALICE_LOCALHOST = "alice@localhost";
    static final String BOB_LOCALHOST = "bob@localhost";

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP_POP3_IMAP)
            .withConfiguration(GreenMailConfiguration.aConfig()
            .withUser(ALICE_LOCALHOST, ALICE, ALICE_PASS)
            .withUser(BOB_LOCALHOST, BOB, BOB_PASS));
    
    final ActorHub hub = new ActorHub();
    
    
    final Actor<SealedNote> inbox = new Actor<>(hub,1,1)
    {
        @Override
        protected void receive(SealedNote m)
        {
            System.out.println(LocalDateTime.now().format(JavaTime.YYYY_MM_DDTHH_MM_SS));
            System.out.println(m.innerEmail+"->"+m.outerEmail);
            System.out.println(m.text);
            System.out.println();
        }
    };

    @Test
    public void testMain1() throws Exception
    {
        Map<String, EmailPreset> map = EmailProviders.load();

        EmailPreset disrootPreset = map.get("disroot");
        ServerSettings disrootSmtp = disrootPreset.getSmtp();
        ServerSettings disrootImap = disrootPreset.getImap();

        EmailPreset gmailPreset = map.get("disroot");
        ServerSettings gmailSmtp = gmailPreset.getSmtp();
        ServerSettings gmailImap = gmailPreset.getImap();

        String disrootPass = PASS.getKey("lettera/francitoshi@disroot.org");
        String gmailPass = PASS.getKey("mutt/flikxxi@gmail.com");
        
//        SmtpActor disrootSmtpActor = new SmtpActor(disrootSmtp, "francitoshi@disroot.org", new SecureChars(disrootPass.toCharArray()), "francitoshi@disroot.org");
//        ImapActor disrootImapActor = new ImapActor(disrootImap, "francitoshi@disroot.org", new SecureChars(disrootPass.toCharArray())).setOut(inbox);
//        
//        SmtpActor gmailSmtpActor = new SmtpActor(gmailSmtp, "flikxxi@gmail.com", new SecureChars(gmailPass.toCharArray()), "flikxxi@gmail.com");
//        ImapActor gmailImapActor = new ImapActor(gmailImap, "flikxxi@gmail.com", new SecureChars(gmailPass.toCharArray())).setOut(inbox);
//        
//        SealedNote note1 = new SealedNote("francitoshi@disroot.org", "flikxxi@gmail.com", JavaTime.epochSecond(), "hola, bienvenido");
//        
//        disrootSmtpActor.send(note1);
//        disrootImapActor.open();
//        
//        SealedNote note2 = new SealedNote("flikxxi@gmail.com", "francitoshi@disroot.org", JavaTime.epochSecond(), "hola, bien hallado");
//        
//        gmailSmtpActor.send(note2);
//        gmailImapActor.open();
//        
//        disrootSmtpActor.awaitTermination(60_000);
//        disrootImapActor.awaitTermination(60_000);
//        
//        gmailSmtpActor.awaitTermination(60_000);
//        gmailImapActor.awaitTermination(60_000);
//        
//        disrootSmtpActor.close();
//        disrootImapActor.close();
//
//        gmailSmtpActor.close();
//        gmailImapActor.close();
    }
    
    private static final String EMAIL = "alice@dummy.org";
    private static final String PASSPHRASE = "PASSPHRASE";
    
    final GPG gpg = new GPG().setDebug(true);
    
    
    @Test
    public void testMain2() throws Exception
    {
        Map<String, EmailPreset> map = EmailProviders.load();
        EmailPreset ep = map.get("disroot");
        ServerSettings smtp = ep.getSmtp();
        ServerSettings imap = ep.getImap();
        ServerSettings pop3 = ep.getPop3();
        
        final String innerAddress = "francitoshi@disroot.org";
        final String innerKeyId = "francitoshi@disroot.org";

        if(gpg.getSecKeys(EMAIL).length==0)
        {
            gpg.genKey(RSA1024, GPG.ESCA, "dummy", "", EMAIL, PASSPHRASE, "4y");
        }
        
//        PipeActor<SealedNote,String> smtpActor = hive.pipe((x) -> x.text);
//        PipeActor<String,SealedNote> imapActor = hive.pipe((x) -> new SealedNote("id1",true, "innerAddress", "innerAddress", JavaTime.epochSecond(), x));
//        smtpActor.setOut(imapActor);
//        imapActor.setOut(inbox);
//        
////        EncodeGpgActor encodeGpgActor = new EncodeGpgActor(innerAddress, innerKeyId, passphrase, pass, pass, skip);
//        
//        SealedNote note = new SealedNote(innerAddress, "flikxxi@gmail.com", JavaTime.epochSecond(), "hola mundo");
//        
//        smtpActor.send(note);
//        
//        smtpActor.awaitTermination(10_000);
//        imapActor.awaitTermination(10_000);
//        
//        gpg.deletePubKeys(EMAIL);
    }
    
//    static class DummyImapActor extends PipeActor<SealedNote,SealedNote>
//    {
//        final SKIP skip;
//
//        public DummyImapActor(SKIP skip)
//        {
//            this.skip = skip;
//        }
//        
//        @Override
//        protected SealedNote process(SealedNote note)
//        {
//            try
//            {
//                String reply = skip.replyChallenge(1, note.text);
//                return new SealedNote(note.innerAddress, note.outerAddress, note.time, reply);
//            }
//            catch (Exception ex)
//            {
//                System.getLogger(MailBotTest.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
//            }
//            return null;
//        }
//    }
    
    @Test
    public void testMain3() throws Exception
    {
        Kripto kripto = Kripto.getInstance();
        SKIP skip = new SKIP(kripto, "hola".toCharArray());
        
//        DummyImapActor aliceImapActor = new DummyImapActor(skip);
//        DummyImapActor bobImapActor = new DummyImapActor(skip);

//        PipeActor<SealedNote,SealedNote> aliceSmtpActor = PipeActor.pipe((x) -> x);
//        PipeActor<SealedNote,SealedNote> bobSmtpActor = PipeActor.pipe((x) -> x);
        
//        aliceSmtpActor.setOut(bobImapActor);
//        bobSmtpActor.setOut(aliceImapActor);
//
////        aliceImapActor.setOut(aliceSmtpActor);
//        bobImapActor.setOut(bobSmtpActor);
//
//        String challenge = skip.buildChallenge(1, "francitoshi@disroot.org;000666;flikxxi@gmail.com");
//        SealedNote note = new SealedNote("francitoshi@disroot.org", "flikxxi@gmail.com", JavaTime.epochSecond(), challenge);
//        
//        aliceSmtpActor.send(note);
        
    }
    
}
