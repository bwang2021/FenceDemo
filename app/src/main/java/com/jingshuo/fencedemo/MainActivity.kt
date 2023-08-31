package com.jingshuo.fencedemo

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.amap.api.fence.GeoFence
import com.amap.api.fence.GeoFenceClient
import com.amap.api.fence.GeoFenceListener
import com.amap.api.location.*
import com.amap.api.maps.*
import com.amap.api.maps.model.*
import com.google.gson.Gson
import com.jeremyliao.liveeventbus.LiveEventBus
import com.jingshuo.fencedemo.databinding.ActivityMainBinding
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : CheckPermissionsActivity(), AMapLocationListener, GeoFenceListener,
    LocationSource, LifecycleOwner {

    private lateinit var mBinding: ActivityMainBinding

    //显示地图需要的变量
    private var mapView: MapView? = null//地图控件
    private var aMap: AMap? = null//地图对象

    //定位需要的声明
    private var mLocationClient: AMapLocationClient? = null //定位发起端
    private var mLocationOption: AMapLocationClientOption? = null //定位参数
    private var mListener: LocationSource.OnLocationChangedListener? = null //定位监听器

    //标识，用于判断是否只显示一次定位信息和用户重新定位
    private var isFirstLoc = true
    private var mLocation: LatLng? = null

    //实例化地理围栏客户端
    var fenceClient: GeoFenceClient? = null

    // 地理围栏的广播action
    private val GEOFENCE_BROADCAST_ACTION = "com.example.geofence.round"

    private var fences: ArrayList<FenceInfoBean>? = null //围栏数据

    // 记录已经添加成功的围栏
    private val fenceMap = HashMap<String, GeoFence>()

    @SuppressLint("CutPasteId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //LiveEventBus配置
        LiveEventBus
            .config()
            .autoClear(true)
            .lifecycleObserverAlwaysActive(true)

        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        mBinding.lifecycleOwner = this
        mBinding.apply {
            mapView = mBinding.map
            aMap = mBinding.map.map
            lifecycleOwner = this@MainActivity
            button.setOnClickListener {
                drawFence(data)
            }

            button1.setOnClickListener {
                startActivity(Intent(this@MainActivity, TaskActivity::class.java))
            }

        }
        initMap(savedInstanceState)

        LiveEventBus.get("ChangeTask", Int::class.java).observe(this) {
            Log.e("TAG", "startObserve: ----------------------------------------")
            drawFence(if (it == 0) data else data1)
        }
    }

    /**
     * 方法必须重写
     */
    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    /**
     * 方法必须重写
     */
    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    /**
     * 方法必须重写
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    /**
     * 方法必须重写
     */
    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }

    private fun initMap(savedInstanceState: Bundle?) {
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)
        //显示地图
        //必须要写
        mBinding.map.onCreate(savedInstanceState)
        //获取地图对象
        if (aMap == null) {
            aMap = mBinding.map.map
        }

        //设置显示定位按钮 并且可以点击
        var settings = aMap!!.uiSettings
        //设置定位监听
        aMap!!.setLocationSource(this)
        // 是否显示定位按钮
        settings.isMyLocationButtonEnabled = true
        settings.isZoomControlsEnabled = false //是否允许显示缩放按钮
        // 是否可触发定位并显示定位层
        aMap!!.isMyLocationEnabled = true

        val myLocationStyle = MyLocationStyle()
//        myLocationStyle.myLocationIcon(BitmapDescriptorFactory.fromResource(R.mipmap.location))
        myLocationStyle.radiusFillColor(android.R.color.transparent)
        myLocationStyle.strokeColor(android.R.color.transparent)
        aMap!!.myLocationStyle = myLocationStyle

        //开始定位

        try {
            initLoc()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        fenceClient = GeoFenceClient(this)
        /**
         * 创建pendingIntent
         */
        fenceClient!!.createPendingIntent(GEOFENCE_BROADCAST_ACTION)
        fenceClient!!.setGeoFenceListener(this)
        fenceClient!!.setActivateAction(3)
    }

    //定位
    @Throws(java.lang.Exception::class)
    private fun initLoc() {
        //初始化定位
        mLocationClient = AMapLocationClient(this)
        //设置定位回调监听
        mLocationClient!!.setLocationListener(this)
        //初始化定位参数
        mLocationOption = AMapLocationClientOption()
        //设置定位模式为高精度模式，Battery_Saving为低功耗模式，Device_Sensors是仅设备模式
        mLocationOption!!.locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
        //设置是否返回地址信息（默认返回地址信息）
        mLocationOption!!.isNeedAddress = true
        //设置是否只定位一次,默认为false
        mLocationOption!!.isOnceLocation = false
        //设置是否强制刷新WIFI，默认为强制刷新
        mLocationOption!!.isWifiActiveScan = true
        //设置是否允许模拟位置,默认为false，不允许模拟位置
        mLocationOption!!.isMockEnable = false
        //设置定位间隔,单位毫秒,默认为2000ms
        mLocationOption!!.interval = 1000 * 200
        //给定位客户端对象设置定位参数
        mLocationClient!!.setLocationOption(mLocationOption)
        //启动定位
        mLocationClient!!.startLocation()
    }

    val data1 =
        "{\"code\":0,\"msg\":\"success\",\"data\":[{\"id\":\"1694307487894818818\",\"name\":\"地震禁止进入框选\",\"type\":\"0\",\"startTime\":\"2023-08-22 19:15:56\",\"endTime\":\"2023-08-31 19:16:01\",\"members\":null,\"rescuePopulation\":null,\"list\":[{\"lng\":120.55927595829428,\"lat\":31.284440641842078},{\"lng\":120.55927595829428,\"lat\":31.287997090471443},{\"lng\":120.56717446908849,\"lat\":31.287997090471443},{\"lng\":120.56717446908849,\"lat\":31.284440641842078}],\"radius\":\"\",\"form\":\"0\"}]}"
    val data =
        "{\"code\":0,\"msg\":\"success\",\"data\":[{\"id\":\"1694303295226597377\",\"name\":\"0823圆形禁止离开\",\"type\":\"1\",\"startTime\":\"2023-08-23 00:00:00\",\"endTime\":\"2023-08-31 23:59:59\",\"members\":null,\"rescuePopulation\":null,\"list\":[{\"lng\":120.54947615075739,\"lat\":31.283327282765804}],\"radius\":\"274.2001755838436\",\"form\":\"2\"},{\"id\":\"1694304202731376642\",\"name\":\"常规调度多边形禁止进入\",\"type\":\"0\",\"startTime\":\"2023-08-01 19:02:55\",\"endTime\":\"2023-08-31 19:03:00\",\"members\":null,\"rescuePopulation\":null,\"list\":[{\"lng\":120.57332202395573,\"lat\":31.258931467556586},{\"lng\":120.56608414215034,\"lat\":31.25734693529068},{\"lng\":120.56783242277965,\"lat\":31.255313911540185},{\"lng\":120.57003525637258,\"lat\":31.25618094172972},{\"lng\":120.57402133620744,\"lat\":31.256868580700303}],\"radius\":\"\",\"form\":\"1\"},{\"id\":\"1696030276720828418\",\"name\":\"任务调度\",\"type\":\"0\",\"startTime\":\"2023-08-28 13:22:20\",\"endTime\":\"2023-08-29 13:22:25\",\"members\":null,\"rescuePopulation\":null,\"list\":[{\"lng\":120.55463615298423,\"lat\":31.15476620113231},{\"lng\":120.55463615298423,\"lat\":31.25256873212129},{\"lng\":120.84605588467076,\"lat\":31.25256873212129},{\"lng\":120.84605588467076,\"lat\":31.15476620113231}],\"radius\":\"\",\"form\":\"0\"},{\"id\":\"1696038670525571074\",\"name\":\"常归\",\"type\":\"0\",\"startTime\":\"2023-08-28 13:55:47\",\"endTime\":\"2023-08-29 13:55:49\",\"members\":null,\"rescuePopulation\":null,\"list\":[{\"lng\":120.57076892965004,\"lat\":31.20030248132081},{\"lng\":120.57076892965004,\"lat\":31.244055415285477},{\"lng\":120.79147904446093,\"lat\":31.244055415285477},{\"lng\":120.79147904446093,\"lat\":31.20030248132081}],\"radius\":\"\",\"form\":\"0\"},{\"id\":\"1696042543902494722\",\"name\":\"任务调度\",\"type\":\"1\",\"startTime\":\"2023-08-28 14:10:16\",\"endTime\":\"2023-08-29 14:10:19\",\"members\":null,\"rescuePopulation\":null,\"list\":[{\"lng\":120.61848075851273,\"lat\":31.28808152837912},{\"lng\":120.82237159552308,\"lat\":31.269886558882053},{\"lng\":120.79182229502824,\"lat\":31.31800754838243},{\"lng\":120.64525430276659,\"lat\":31.351149783575035},{\"lng\":120.59864850583766,\"lat\":31.28899444610188}],\"radius\":\"\",\"form\":\"1\"}]}"

    private fun drawFence(data: String) {
        val jsonObject = JSONObject(data)
        val fencesData = jsonObject.getString("data")
        val videoMeetingResponse = Gson().fromJson(fencesData, FenceResponse::class.java)
        videoMeetingResponse.let {
            fences = ArrayList()
            polygons.forEach {
                it.value.remove()
            }

            circles.forEach {
                it.value.remove()
            }

            // 清除旧围栏
            for (geoFence in fenceMap.values) {
                fenceClient?.removeGeoFence(geoFence)
            }
            fenceMap.clear() // 清空围栏映射


            it.forEach { fence ->
                Log.e("TAG", "startObserve: ------------" + fence.id + "///" + fence.name)
                fences!!.add(FenceInfoBean(fence.id, fence.type))
                if (fence.form == "2") {
                    val centerPoint = DPoint(
                        fence.list[0].lat,
                        fence.list[0].lng
                    )
                    val fenceRadius = fence.radius.toFloat()
                    fenceClient?.addGeoFence(
                        centerPoint,
                        fenceRadius,
                        fence.name.plus(fence.id)
                    )
                } else {
                    val points: MutableList<DPoint> = ArrayList()
                    fence.list.forEach {
                        points.add(DPoint(it.lat, it.lng))
                    }

                    fenceClient?.addGeoFence(points, fence.name.plus(fence.id))
                }

            }
        }

    }

    private var handler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                0 -> {
                    val sb = StringBuffer()
                    sb.append("添加围栏成功")
                    val fence = msg.obj as GeoFence
                    sb.append("customId: ").append(fence.customId)
                    Log.e("TAG", "handleMessage: " + sb)
                    drawFence2Map(fence)
                }
                else -> {}
            }
        }
    }


    var lock = Any()
    fun drawFence2Map(geoFence: GeoFence) {
        object : Thread() {
            override fun run() {
                try {
                    synchronized(lock) {
                        if (fenceMap.isEmpty()) {
                            return
                        }

                        drawFence(geoFence)
                        Log.e("TAG", "run: 正在绘制-------->" + geoFence.customId)
                    }
                } catch (e: Throwable) {
                }
            }
        }.start()
    }


    private fun drawFence(fence: GeoFence) {
        when (fence.type) {
            GeoFence.TYPE_ROUND, GeoFence.TYPE_AMAPPOI -> drawCircle(fence)
            GeoFence.TYPE_POLYGON, GeoFence.TYPE_DISTRICT -> drawPolygon(fence)
            else -> {}
        }

        // // 设置所有maker显示在当前可视区域地图中
        // LatLngBounds bounds = boundsBuilder.build();
        // mAMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200));
    }

    //记录已绘制圆形
    private val circles = HashMap<String, Circle>()

    private fun drawCircle(fence: GeoFence) {
        circles.forEach {
            if (it.key == fence.customId) {
                it.value.remove()
            }
        }
        val center = LatLng(
            fence.center.latitude,
            fence.center.longitude
        )

        var stroke: Int = Color.argb(255, 237, 58, 53)
        var fill: Int = Color.argb(80, 237, 58, 53)
        fences?.forEach {
            if (fence.customId.contains(it.id)) {
                stroke = if (it.type == "0")
                    Color.argb(255, 237, 58, 53)
                else
                    Color.argb(255, 19, 207, 141)

                fill = if (it.type == "0")
                    Color.argb(60, 237, 58, 53)
                else
                    Color.argb(60, 19, 207, 141)

            }
        }

        // 绘制一个圆形
        val circle = aMap?.addCircle(
            CircleOptions().center(center)
                .radius(fence.radius.toDouble()).strokeColor(stroke)
                .fillColor(fill).strokeWidth(5f)
        )
        if (circle != null) {
            circles[fence.customId] = circle
        }
    }

    //记录已绘制多边形
    private val polygons = HashMap<String, Polygon>()

    private fun drawPolygon(fence: GeoFence) {
        polygons.forEach {
            if (it.key == fence.customId) {
                it.value.remove()
            }
        }
        val pointList = fence.pointList
        if (null == pointList || pointList.isEmpty()) {
            return
        }
        for (subList in pointList) {
            val lst: MutableList<LatLng> = ArrayList()
            val polygonOption = PolygonOptions()
            for (point in subList) {
                lst.add(LatLng(point.latitude, point.longitude))
            }
            polygonOption.addAll(lst)
            var stroke: Int = Color.argb(255, 237, 58, 53)
            var fill: Int = Color.argb(80, 237, 58, 53)
            fences?.forEach {
                if (fence.customId.contains(it.id)) {
                    stroke = if (it.type == "0")
                        Color.argb(255, 237, 58, 53)
                    else
                        Color.argb(255, 19, 207, 141)

                    fill = if (it.type == "0")
                        Color.argb(80, 237, 58, 53)
                    else
                        Color.argb(80, 19, 207, 141)

                }
            }
            polygonOption.strokeColor(stroke)
                .fillColor(fill).strokeWidth(5f)
            val polygon = aMap?.addPolygon(polygonOption)
            if (polygon != null) {
                polygons[fence.customId] = polygon
            }
        }
    }

    override fun onLocationChanged(amapLocation: AMapLocation?) {
        if (amapLocation != null) {
            if (amapLocation.errorCode == 0) {

                //定位成功回调信息，设置相关消息
                amapLocation.locationType //获取当前定位结果来源，如网络定位结果，详见官方定位类型表
                amapLocation.latitude //获取纬度
                amapLocation.longitude //获取经度
                mLocation = LatLng(amapLocation.latitude, amapLocation.longitude) //初始化自己的位置
                amapLocation.accuracy //获取精度信息
                val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                val date = Date(amapLocation.time)
                df.format(date) //定位时间
                amapLocation.address //地址，如果option中设置isNeedAddress为false，则没有此结果，网络定位结果中会有地址信息，GPS定位不返回地址信息。


                // 如果不设置标志位，此时再拖动地图时，它会不断将地图移动到当前的位置
                if (isFirstLoc) {
                    //设置缩放级别
                    aMap!!.moveCamera(CameraUpdateFactory.zoomTo(17f))
                    //将地图移动到定位点
                    aMap!!.moveCamera(
                        CameraUpdateFactory.changeLatLng(
                            LatLng(
                                amapLocation.latitude,
                                amapLocation.longitude
                            )
                        )
                    )
                    //点击定位按钮 能够将地图的中心移动到定位点
                    mListener!!.onLocationChanged(amapLocation)
                    //获取定位信息
                    val buffer = StringBuffer()
                    buffer.append(amapLocation.country + "" + amapLocation.province + "" + amapLocation.city + "" + amapLocation.province + "" + amapLocation.district + "" + amapLocation.street + "" + amapLocation.streetNum)
                    Toast.makeText(this, buffer.toString(), Toast.LENGTH_LONG)
                        .show()
                    isFirstLoc = false
                }
            } else {
                Toast.makeText(this, "定位失败", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onGeoFenceCreateFinished(
        geoFenceList: MutableList<GeoFence>?,
        errorCode: Int,
        p2: String?
    ) {
        val msg = Message.obtain()
        Log.e("TAG", "onGeoFenceCreateFinished: " + errorCode)
        if (errorCode == GeoFence.ADDGEOFENCE_SUCCESS) {
            Log.e("TAG", "onGeoFenceCreateFinished: " + geoFenceList!![0].customId)

            fenceMap[geoFenceList[0].customId] = geoFenceList[0]
            msg.obj = geoFenceList[0]
            msg.what = 0
        } else {
            msg.arg1 = errorCode
            msg.what = 1
        }
        handler.sendMessage(msg)
    }

    override fun activate(p0: LocationSource.OnLocationChangedListener?) {
    }

    override fun deactivate() {
    }

    private val lifecycleRegistry = LifecycleRegistry(this)

    init {
        // 初始化 LifecycleRegistry，并添加需要的状态转换
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }


    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry // 返回当前组件的生命周期对象
    }


}