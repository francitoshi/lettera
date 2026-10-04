package io.francitoshi.lettera.actor;

import io.francitoshi.lettera.LetteraDb;
import io.francitoshi.lettera.actor.msg.*;
import io.francitoshi.lettera.data.Friend;
import io.francitoshi.lettera.data.Sender;
import io.nut.base.concurrent.actor.ActorHub;
import io.nut.base.concurrent.actor.Publisher;
import io.nut.base.concurrent.actor.Subscription;

import java.util.function.Consumer;

public class StoreActor implements AutoCloseable {
    private final LetteraDb db;
    private final Subscription<StoreGetFriend> subGetFriend;
    private final Subscription<StorePutFriend> subPutFriend;
    private final Subscription<StoreGetSender> subGetSender;
    private final Subscription<StorePutSender> subPutSender;
    private final Publisher<StoreFriendResp> pubFriendResp;
    private final Publisher<StoreSenderResp> pubSenderResp;

    public StoreActor(ActorHub hub, LetteraDb db) {
        this.db = db;
        this.pubFriendResp = hub.pub("StoreFriendResp");
        this.pubSenderResp = hub.pub("StoreSenderResp");
        this.subGetFriend = hub.sub("StoreGetFriend", new Consumer<StoreGetFriend>() {
            @Override
            public void accept(StoreGetFriend m) {
                Friend f = db.getFriend(m.id);
                if (m.replyTo != null && m.replyTo instanceof Consumer) {
                    @SuppressWarnings("unchecked")
                    Consumer<Friend> c = (Consumer<Friend>) m.replyTo;
                    c.accept(f);
                } else {
                    pubFriendResp.accept(new StoreFriendResp(f));
                }
            }
        });
        this.subPutFriend = hub.sub("StorePutFriend", new Consumer<StorePutFriend>() {
            @Override
            public void accept(StorePutFriend m) {
                db.putFriend(m.friend);
                db.commit();
            }
        });
        this.subGetSender = hub.sub("StoreGetSender", new Consumer<StoreGetSender>() {
            @Override
            public void accept(StoreGetSender m) {
                Sender s = db.getSender();
                if (m.replyTo != null && m.replyTo instanceof Consumer) {
                    @SuppressWarnings("unchecked")
                    Consumer<Sender> c = (Consumer<Sender>) m.replyTo;
                    c.accept(s);
                } else {
                    pubSenderResp.accept(new StoreSenderResp(s));
                }
            }
        });
        this.subPutSender = hub.sub("StorePutSender", new Consumer<StorePutSender>() {
            @Override
            public void accept(StorePutSender m) {
                db.putSender(m.sender);
                db.commit();
            }
        });
    }

    @Override
    public void close() {
        try {
            subGetFriend.close();
        } catch (Exception ignored) {
        }
        try {
            subPutFriend.close();
        } catch (Exception ignored) {
        }
        try {
            subGetSender.close();
        } catch (Exception ignored) {
        }
        try {
            subPutSender.close();
        } catch (Exception ignored) {
        }
    }
}
