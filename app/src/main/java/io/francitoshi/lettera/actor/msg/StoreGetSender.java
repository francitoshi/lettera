package io.francitoshi.lettera.actor.msg;

import java.io.Serializable;

public class StoreGetSender implements Serializable {
    private static final long serialVersionUID = 1L;
    public final Object replyTo;

    public StoreGetSender(Object replyTo) {
        this.replyTo = replyTo;
    }
}
