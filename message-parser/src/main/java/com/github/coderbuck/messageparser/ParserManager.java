package com.github.coderbuck.messageparser;

import com.github.coderbuck.messageparser.bean.MessageBean;

import java.util.ArrayList;
import java.util.List;

public class ParserManager {

    private static List<BaseParser> parsers = new ArrayList<>();

    private static NvCallback callback = new NvCallback() {
        @Override
        public void callback(String msg) {
            MessageBean messageBean = GsonUtils.fromJson(msg, MessageBean.class);
            String head = messageBean.getHead();

            for (BaseParser parser : parsers) {
                if (parser.hasMsg(head)) {
                    // 保存原始信息日志
                    System.out.println("msg = " + msg);

                    parser.parse(messageBean);
                }
            }
        }
    };

    public static NvCallback getCallback() {
        return callback;
    }

    public static void register(BaseParser parser) {
        parsers.add(parser);
    }

    public static void unregister(BaseParser parser) {
        parsers.remove(parser);
    }
}

