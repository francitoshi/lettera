/*
 * Copyright (C) 2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.lettera.bot;

import io.francitoshi.lettera.LetteraDb;
import io.francitoshi.lettera.data.PlainNote;
import io.francitoshi.lettera.data.SealedNote;
import io.nut.base.crypto.gpg.GPG;
import io.nut.base.crypto.gpg.GPG.DecryptStatus;
import io.nut.base.time.JavaTime;
import io.nut.base.concurrent.actor.Actor;
import io.nut.base.concurrent.actor.ActorHub;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HubBot
{
    final GPG gpg = new GPG().setArmor(true).setDebug(true);    

    private final ActorHub hive;
    private final LetteraDb db;
    private final char[] gpgPassphrase;
    private final HashMap<String,Map<Long, PlainNote>> chats = new HashMap<>();
    private final Map<Long, SealedNote> sealedNotes = new HashMap<>();
    
    public HubBot(ActorHub hive, LetteraDb db, char[] gpgPassphrase)
    {
        this.hive = hive;
        this.db = db;
        this.gpgPassphrase = gpgPassphrase;
        this.hive.sub("PlainNote", this::onPlainNote);
        this.hive.sub("SealedNote", this::onSealedNote);
    }
    
    public Map<Long, PlainNote> getchat(String chatId)
    {
        return chats.computeIfAbsent(chatId, (x) -> db.openChat(x));
    }

    public void onPlainNote(PlainNote m)
    {
        try 
        {
            if(!m.isStored())
            {
                storeNote(m);
                m.setStored(JavaTime.epochSecond());
            }
            if(!m.isShowed())
            {
                showNote(m);
                m.setShowed(JavaTime.epochSecond());
            }
            if(!m.isSent())
            {
                SealedNote s = plainToSealed(m);
//666                sealedNotes.put(s.id, s);
                sendNote(s);
            }
            if(m.isModified())
            {
                storeNote(m);
            }
        }
        catch (IOException | InterruptedException ex) 
        {
            System.getLogger(HubBot.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }        
    }

    public void onSealedNote(SealedNote s)
    {
        if(s.mine)
        {
            PlainNote pn = findPlainNote(s);
        }
    }
    
    public SealedNote plainToSealed(PlainNote plain) throws IOException, InterruptedException
    {
        byte[] plaintext = plain.text.getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = gpg.encryptAndSign(plaintext, plain.innerFingerprint, gpgPassphrase, plain.outerFingerprint);
//666        return new SealedNote(plain.id, plain.mine, plain.innerAddress, plain.innerKeyId, plain.outerAddress, plain.outerKeyId, new String(ciphertext, StandardCharsets.UTF_8));
        return null;
    }
    
    public PlainNote sealedToPlain(SealedNote sealed) throws IOException, InterruptedException
    {
        byte[] cipherdate = sealed.text.getBytes(StandardCharsets.UTF_8);
        DecryptStatus status = new DecryptStatus();
        byte[] plaintext = gpg.decryptAndVerify(cipherdate, gpgPassphrase, status);
        long created = JavaTime.epochSecond();
//666        return new PlainNote(sealed.id, created, sealed.mine, sealed.innerAddress, sealed.innerKeyId, sealed.outerAddress, sealed.outerKeyId, new String(plaintext, StandardCharsets.UTF_8));
        return null;
    }

    public boolean storeNote(PlainNote plain)
    {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    
    public final java.util.function.Consumer<PlainNote> plainNoteActor = (m) ->
    {
    };

    private void showNote(PlainNote m)
    {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    private void sendNote(SealedNote m)
    {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    private PlainNote findPlainNote(SealedNote s)
    {
        return null;//666 plainNotes.getOrDefault(s.id, null);
    }
    
}
