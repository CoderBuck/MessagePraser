package com.github.coderbuck.messageparser.sample;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.coderbuck.messageparser.EmMsg;
import com.github.coderbuck.messageparser.GsonUtils;
import com.github.coderbuck.messageparser.ParserManager;
import com.github.coderbuck.messageparser.bean.ABean;
import com.github.coderbuck.messageparser.bean.BBean;
import com.github.coderbuck.messageparser.bean.CBean;
import com.github.coderbuck.messageparser.bean.DBean;
import com.github.coderbuck.messageparser.bean.MessageBean;

import kd.message.parser.AiHandler;
import kd.message.parser.AiInterceptor;
import kd.message.parser.AiParser;
import kd.message.parser.ConfHandler;
import kd.message.parser.ConfParser;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ParserManager.register(new AiParser());
        ParserManager.register(new ConfParser());
        mAiInterceptor.register();
        mAiHandler.register();
        mAiHandler2.register();
        mConfHandler.register();

        call(EmMsg.A);
        call(EmMsg.B);
        call(EmMsg.C);
        call(EmMsg.D);
        call(EmMsg.E);
        callE();


    }

    private void call(EmMsg emMsg) {
        String head = emMsg.name();
        String body = GsonUtils.toJson(new ABean("xxxx.." + head));
        MessageBean bean = new MessageBean(head,body);
        String json = GsonUtils.toJson(bean);
        ParserManager.getCallback().callback(json);
    }

    private void callE() {
        String head = EmMsg.E.name();
        String body = "{}";
        MessageBean bean = new MessageBean(head,body);
        String json = GsonUtils.toJson(bean);
        ParserManager.getCallback().callback(json);
    }


    AiHandler mAiHandler = new AiHandler() {
        @Override
        public void A(ABean body) {
            Log.d(TAG, "mAiHandler A: " + body.name);
        }

        @Override
        public void B(BBean body) {
            Log.d(TAG, "mAiHandler B: " + body.name);
        }

        @Override
        public void C(CBean body) {
            Log.d(TAG, "mAiHandler C: " + body.name);
        }
    };

    AiHandler mAiHandler2 = new AiHandler.Simple(){
        @Override
        public void A(ABean body) {
            Toast.makeText(MainActivity.this, "msg: " + body.name, Toast.LENGTH_SHORT).show();
        }
    };

    AiInterceptor mAiInterceptor = new AiInterceptor() {
        @Override
        public boolean A(ABean body) {
            return false;
        }

        @Override
        public boolean B(BBean body) {
            return true;
        }

        @Override
        public boolean C(CBean body) {
            return false;
        }
    };

    ConfHandler mConfHandler = new ConfHandler() {
        @Override
        public void D(DBean body) {
            Log.d(TAG, "mConfHandler D: " + body.name);
        }

        @Override
        public void E(Object body) {
            Log.d(TAG, "mConfHandler E: Object == null ? " + (body == null));
            if (body != null) {
                Log.d(TAG, "mConfHandler E: Object == null ? " + body.getClass().getCanonicalName());
            }
        }
    };
}
