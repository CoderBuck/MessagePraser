package com.github.coderbuck.messageparser;

import com.github.coderbuck.messageparser.bean.MessageBean;

public interface BaseParser {

    boolean hasMsg(String msg);

    void parse(MessageBean messageBean);
}
