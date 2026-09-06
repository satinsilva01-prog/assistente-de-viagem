package com.assistentedeviagem.app

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var web: WebView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = true
        web.addJavascriptInterface(Bridge(), "AndroidGPS")
        web.webViewClient = WebViewClient()
        web.loadUrl("file:///android_asset/index.html")
        setContentView(web)

        requestLocationPermission()
        handler.post(object : Runnable {
            override fun run() {
                pushGpsToPage()
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ), 100)
        }
    }

    private fun pushGpsToPage() {
        val p = getSharedPreferences("trip", 0)
        val lat = p.getString("lat", "") ?: ""
        val lon = p.getString("lon", "") ?: ""
        val speed = p.getFloat("speed", -1f)
        val accuracy = p.getFloat("accuracy", -1f)
        val distance = p.getFloat("distance_km", 0f)
        val running = p.getBoolean("running", false)
        val data = JSONObject()
        data.put("lat", lat); data.put("lon", lon); data.put("speed", speed)
        data.put("accuracy", accuracy); data.put("distance", distance); data.put("running", running)
        web.evaluateJavascript("window.nativeGpsUpdate && window.nativeGpsUpdate(${JSONObject.quote(data.toString())})", null)
    }

    inner class Bridge {
        @JavascriptInterface fun startTrip() {
            runOnUiThread {
                windowAddKeepScreen()
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    startService(Intent(this@MainActivity, LocationService::class.java))
                } else {
                    requestLocationPermission()
                    Toast.makeText(this@MainActivity, "Permita a localização e toque em Iniciar novamente.", Toast.LENGTH_LONG).show()
                }
            }
        }
        @JavascriptInterface fun stopTrip() {
            stopService(Intent(this@MainActivity, LocationService::class.java))
        }
        @JavascriptInterface fun isNative() = true
    }

    private fun windowAddKeepScreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
