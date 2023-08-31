package com.jingshuo.fencedemo

import android.app.Activity
import android.os.Bundle
import android.view.View
import com.jeremyliao.liveeventbus.LiveEventBus

class TaskActivity : Activity() ,View.OnClickListener{

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task)
    }

    override fun onClick(v: View) {
        if (v.id==R.id.button){
            LiveEventBus.get("ChangeTask").post(0)
        }else{
            LiveEventBus.get("ChangeTask").post(1)
        }
        finish()

    }
}