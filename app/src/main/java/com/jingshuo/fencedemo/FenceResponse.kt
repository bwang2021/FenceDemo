package com.jingshuo.fencedemo

class FenceResponse : ArrayList<FenceResponse.FenceResponseItem>() {
    data class FenceResponseItem(
        val endTime: String,
        val form: String, //0->矩形 1->多边形 2->圆型
        val id: String,
        val list: List<Points>,
        val members: String,
        val name: String,
        val radius: String,
        val rescuePopulation: Any,
        val startTime: String,
        val type: String //0->禁止进入,1->禁止离开,2->禁止出入
    ) {
        data class Points(
            val lat: Double,
            val lng: Double
        )
    }
}