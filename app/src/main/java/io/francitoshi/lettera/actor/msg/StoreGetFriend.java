package io.francitoshi.lettera.actor.msg;

import java.io.Serializable;

public class StoreGetFriend implements Serializable {
    private static final long serialVersionUID = 1L;
    public final String id;
    public final Object replyTo;

    public StoreGetFriend(String id, Object replyTo) {
        this.id = id;
        this.replyTo = replyTo;
    }
}
