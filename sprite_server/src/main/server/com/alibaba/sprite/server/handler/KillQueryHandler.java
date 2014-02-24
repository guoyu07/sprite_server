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
package com.alibaba.sprite.server.handler;

import com.alibaba.sprite.Sprite;
import com.alibaba.sprite.config.ErrorCode;
import com.alibaba.sprite.net.FrontendConnection;
import com.alibaba.sprite.net.NIOProcessor;
import com.alibaba.sprite.server.ServerConnection;
import com.alibaba.sprite.util.StringUtil;

/**
 * @author xianmao.hexm 2012-4-17
 */
public class KillQueryHandler {

    public static void handle(String stmt, int offset, final ServerConnection c) {
        String id = stmt.substring(offset).trim();
        if (StringUtil.isEmpty(id)) {
            c.writeErrMessage(ErrorCode.ER_NO_SUCH_THREAD, "NULL connection id");
        } else {
            // get value
            long value = 0;
            try {
                value = Long.parseLong(id);
            } catch (NumberFormatException e) {
                c.writeErrMessage(ErrorCode.ER_NO_SUCH_THREAD, "Invalid connection id:" + id);
                return;
            }

            // kill query itself
            if (value == c.getId()) {
                c.cancel(null);
                return;
            }

            // get the connection and kill query
            FrontendConnection fc = null;
            NIOProcessor[] processors = Sprite.getInstance().getProcessors();
            for (NIOProcessor p : processors) {
                if ((fc = p.getFrontends().get(value)) != null) {
                    break;
                }
            }
            if (fc != null) {
                if (fc instanceof ServerConnection) {
                    ((ServerConnection) fc).cancel(c);
                } else {
                    c.writeErrMessage(ErrorCode.ER_UNKNOWN_COM_ERROR, "Unknown command");
                }
            } else {
                c.writeErrMessage(ErrorCode.ER_NO_SUCH_THREAD, "Unknown connection id:" + id);
            }
        }
    }

}
