package com.github.coderbuck.messageparser;

import com.github.coderbuck.messageparser.annotation.Body;
import com.github.coderbuck.messageparser.bean.ABean;
import com.github.coderbuck.messageparser.bean.BBean;
import com.github.coderbuck.messageparser.bean.CBean;
import com.github.coderbuck.messageparser.bean.DBean;

public enum EmMsg {
    @Body(ABean.class) A,
    @Body(BBean.class) B,
    @Body(CBean.class) C,
    @Body(DBean.class) D,
    @Body(Object.class) E,
    ;
}
