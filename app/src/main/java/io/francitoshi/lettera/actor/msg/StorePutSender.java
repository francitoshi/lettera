package io.francitoshi.lettera.actor.msg;

import io.francitoshi.lettera.data.Sender;
import java.io.Serializable;

public class StorePutSender implements Serializable {
    private static final long serialVersionUID = 1L;
    public final Sender sender;

    public StorePutSender(Sender sender) {
        this.sender = sender;
    }
}
