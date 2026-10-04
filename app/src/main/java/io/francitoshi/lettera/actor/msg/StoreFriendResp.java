package io.francitoshi.lettera.actor.msg;

import io.francitoshi.lettera.data.Friend;
import java.io.Serializable;

public class StoreFriendResp implements Serializable {
    private static final long serialVersionUID = 1L;
    public final Friend friend;

    public StoreFriendResp(Friend friend) {
        this.friend = friend;
    }
}
