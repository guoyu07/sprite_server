/*
 * Copyright 1999-2012 Alibaba Group.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.sprite.net;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;

import com.alibaba.sprite.util.ErrorCode;

/**
 * @author xianmao.hexm
 */
public final class Reactor {

    private static final Logger LOGGER = Logger.getLogger(Reactor.class);

    private final String name;
    private final R r;
    private final W w;

    public Reactor(String name) throws IOException {
        this.name = name;
        this.r = new R();
        this.w = new W();
    }

    final void startup() {
        new Thread(r, name + "-R").start();
        new Thread(w, name + "-W").start();
    }

    final void postRegister(Connection c) {
        r.registerQueue.offer(c);
        r.selector.wakeup();
    }

    final BlockingQueue<Connection> getRegisterQueue() {
        return r.registerQueue;
    }

    final long getReactCount() {
        return r.reactCount;
    }

    final void postWrite(Connection c) {
        w.writeQueue.offer(c);
    }

    final BlockingQueue<Connection> getWriteQueue() {
        return w.writeQueue;
    }

    private final class R implements Runnable {

        private final Selector selector;
        private final BlockingQueue<Connection> registerQueue;
        private long reactCount;

        private R() throws IOException {
            this.selector = Selector.open();
            this.registerQueue = new LinkedBlockingQueue<Connection>();
        }

        @Override
        public void run() {
            final Selector selector = this.selector;
            for (;;) {
                ++reactCount;
                try {
                    selector.select(1000L);
                    register(selector);
                    Set<SelectionKey> keys = selector.selectedKeys();
                    try {
                        for (SelectionKey key : keys) {
                            Object att = key.attachment();
                            if (att != null && key.isValid()) {
                                int readyOps = key.readyOps();
                                if ((readyOps & SelectionKey.OP_READ) != 0) {
                                    read((Connection) att);
                                } else if ((readyOps & SelectionKey.OP_WRITE) != 0) {
                                    write((Connection) att);
                                } else {
                                    key.cancel();
                                }
                            } else {
                                key.cancel();
                            }
                        }
                    } finally {
                        keys.clear();
                    }
                } catch (Throwable e) {
                    LOGGER.warn(name, e);
                }
            }
        }

        private void register(Selector selector) {
            Connection c = null;
            while ((c = registerQueue.poll()) != null) {
                try {
                    c.register(selector);
                } catch (Throwable e) {
                    c.error(ErrorCode.ERR_REGISTER, e);
                }
            }
        }

        private void read(Connection c) {
            try {
                c.read();
            } catch (Throwable e) {
                c.error(ErrorCode.ERR_READ, e);
            }
        }

        private void write(Connection c) {
            try {
                c.writeByEvent();
            } catch (Throwable e) {
                c.error(ErrorCode.ERR_WRITE_BY_EVENT, e);
            }
        }
    }

    private final class W implements Runnable {

        private final BlockingQueue<Connection> writeQueue;

        private W() {
            this.writeQueue = new LinkedBlockingQueue<Connection>();
        }

        @Override
        public void run() {
            Connection c = null;
            for (;;) {
                try {
                    if ((c = writeQueue.take()) != null) {
                        write(c);
                    }
                } catch (Throwable e) {
                    LOGGER.warn(name, e);
                }
            }
        }

        private void write(Connection c) {
            try {
                c.writeByQueue();
            } catch (Throwable e) {
                c.error(ErrorCode.ERR_WRITE_BY_QUEUE, e);
            }
        }
    }

}
