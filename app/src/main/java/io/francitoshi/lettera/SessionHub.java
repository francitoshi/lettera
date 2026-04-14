package io.francitoshi.lettera;

import io.nut.base.util.LongNonce;
import io.nut.base.util.concurrent.hive.Bee;
import io.nut.base.util.concurrent.hive.Hive;
import java.util.Map;

public class SessionHub extends Bee<Note>
{
    private final LongNonce nonce = LongNonce.getCurrentMillisInstance();//666 cambiar por EpochSecond
    
    private final Account currentAccount;
    private final Friend currentFriend;
    private final Chat currentChat;
    private final Map<Long, Note> currentNotes;

    private final LetteraDb db;
    private final Lettera.Mode mode = Lettera.Mode.ReadWrite;
    private final Bee<Note> showBee;
    private final MailPush mailPush;
    private final MailPoll mailPoll;

    public SessionHub(Account currentAccount, Friend currentFriend, Chat currentChat, Map<Long, Note> currentNotes, LetteraDb db, Bee<Note> showBee, MailPush mailPush, MailPoll mailPoll, int threads, Hive hive)
    {
        super(threads, hive);
        this.currentAccount = currentAccount;
        this.currentFriend = currentFriend;
        this.currentChat = currentChat;
        this.currentNotes = currentNotes;
        this.db = db;
        this.showBee = showBee;
        this.mailPush = mailPush;
        this.mailPoll = mailPoll;
    }

    @Override
    protected void receive(Note note)
    {
        this.currentNotes.put(note.id, note);
        db.commit();
        if(!note.isShowed())
        {
            showBee.send(note);
        }
        if(note.getSent()==0)
        {
            mailPush.send(note);
        }
    }
    
}
