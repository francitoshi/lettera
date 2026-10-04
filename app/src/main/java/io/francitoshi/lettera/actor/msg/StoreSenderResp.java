package io.francitoshi.lettera.actor.msg;

import io.francitoshi.lettera.data.Sender;
import java.io.Serializable;

public class StoreSenderResp implements Serializable {
    private static final long serialVersionUID = 1L;
    public final Sender sender;

    public StoreSenderResp(Sender sender) {
        this.sender = sender;
    }
}
