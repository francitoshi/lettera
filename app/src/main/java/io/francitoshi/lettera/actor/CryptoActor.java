package io.francitoshi.lettera.actor;

import io.francitoshi.lettera.KeyWrapper;
import io.nut.base.concurrent.actor.ActorHub;
import io.nut.base.concurrent.actor.Publisher;
import io.nut.base.concurrent.actor.Subscription;

import java.io.Serializable;
import java.util.function.Consumer;

public class CryptoActor implements AutoCloseable {
    public static class WrapReq implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String purpose;
        public final String name;
        public final char[] password;

        public WrapReq(String purpose, String name, char[] password) {
            this.purpose = purpose;
            this.name = name;
            this.password = password;
        }
    }

    public static class WrapResp implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String wrapped;

        public WrapResp(String wrapped) {
            this.wrapped = wrapped;
        }
    }

    public static class UnwrapReq implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String purpose;
        public final String name;
        public final String wrapped;
        public final Object replyTo;

        public UnwrapReq(String purpose, String name, String wrapped, Object replyTo) {
            this.purpose = purpose;
            this.name = name;
            this.wrapped = wrapped;
            this.replyTo = replyTo;
        }
    }

    public static class UnwrapResp implements Serializable {
        private static final long serialVersionUID = 1L;
        public final char[] password;

        public UnwrapResp(char[] password) {
            this.password = password;
        }
    }

    private final KeyWrapper keyWrapper;
    private final Subscription<WrapReq> subWrap;
    private final Subscription<UnwrapReq> subUnwrap;
    private final Publisher<WrapResp> pubWrap;
    private final Publisher<UnwrapResp> pubUnwrap;

    public CryptoActor(ActorHub hub, KeyWrapper keyWrapper) {
        this.keyWrapper = keyWrapper;
        this.pubWrap = hub.pub("CryptoWrapResp");
        this.pubUnwrap = hub.pub("CryptoUnwrapResp");
        this.subWrap = hub.sub("CryptoWrapReq", new Consumer<WrapReq>() {
            @Override
            public void accept(WrapReq m) {
                String w = keyWrapper.wrapKey(m.purpose, m.name, m.password);
                pubWrap.accept(new WrapResp(w));
            }
        });
        this.subUnwrap = hub.sub("CryptoUnwrapReq", new Consumer<UnwrapReq>() {
            @Override
            public void accept(UnwrapReq m) {
                char[] pw = keyWrapper.unwrapKey(m.purpose, m.name, m.wrapped);
                if (m.replyTo != null && m.replyTo instanceof Consumer) {
                    @SuppressWarnings("unchecked")
                    Consumer<char[]> c = (Consumer<char[]>) m.replyTo;
                    c.accept(pw);
                } else {
                    pubUnwrap.accept(new UnwrapResp(pw));
                }
            }
        });
    }

    @Override
    public void close() {
        try {
            subWrap.close();
        } catch (Exception ignored) {
        }
        try {
            subUnwrap.close();
        } catch (Exception ignored) {
        }
    }
}
