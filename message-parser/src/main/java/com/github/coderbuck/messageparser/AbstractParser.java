package com.github.coderbuck.messageparser;

import com.github.coderbuck.messageparser.bean.MessageBean;
import com.github.coderbuck.messageparser.function.MessageConsumer;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractParser implements BaseParser {

    private Map<String, MessageConsumer> consumerMap = new HashMap<>();

    public AbstractParser() {
        init();
    }

    protected abstract void init();

    public void addMsg(String msg, MessageConsumer consumer) {
        consumerMap.put(msg, consumer);
    }

    @Override
    public boolean hasMsg(String msg) {
        return consumerMap.keySet().contains(msg);
    }

    @Override
    public void parse(MessageBean messageBean) {
        MessageConsumer consumer = consumerMap.get(messageBean.getHead());
        consumer.accept(messageBean);
    }
}
