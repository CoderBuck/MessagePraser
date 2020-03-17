package com.github.coderbuck.messageparser.function;

import com.github.coderbuck.messageparser.bean.MessageBean;

public interface MessageConsumer {

    void accept(MessageBean messageBean);
}
