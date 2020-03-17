package com.github.coderbuck.messageparser.annotation;

import com.github.coderbuck.messageparser.EmMsg;

public @interface Message {
    EmMsg[] value();
}
