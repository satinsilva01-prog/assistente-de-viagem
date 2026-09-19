package com.assistentedeviagem.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    companion object { private const val LOCATION_REQUEST = 1001 }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(AndroidBridge(), "AndroidGPS")
        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
        solicitarPermissoes()
        handler.post(object : Runnable { override fun run() { enviarGpsParaPagina(); handler.postDelayed(this, 1000) } })
    }
    private fun solicitarPermissoes() {
        val p = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.POST_NOTIFICATIONS)
        if (p.isNotEmpty()) requestPermissions(p.toTypedArray(), LOCATION_REQUEST)
    }
    private fun temLocalizacao(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED else true
    private fun iniciarServicoGps() {
        if (!temLocalizacao()) { Toast.makeText(this, "Permita a localização do aparelho e toque em Iniciar novamente.", Toast.LENGTH_LONG).show(); solicitarPermissoes(); return }
        try { val i=Intent(this,LocationService::class.java); if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) startForegroundService(i) else startService(i) } catch(e:Exception){ Toast.makeText(this,"Não foi possível iniciar o GPS: ${e.message ?: "erro"}",Toast.LENGTH_LONG).show() }
    }
    private fun pararServicoGps(){ try{stopService(Intent(this,LocationService::class.java))}catch(_:Exception){} }
    private fun resetAplicativoNativo() {
        runOnUiThread {
            try {
                pararServicoGps()
                getSharedPreferences("trip", MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("app", MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("vehicle", MODE_PRIVATE).edit().clear().apply()
                webView.stopLoading()
                webView.clearHistory()
                webView.clearFormData()
                webView.clearCache(true)
                WebStorage.getInstance().deleteAllData()
                webView.evaluateJavascript(
                    "try{localStorage.clear();sessionStorage.clear();}catch(e){}",
                    null
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Erro ao resetar dados nativos: " + (e.message ?: "erro"), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enviarGpsParaPagina(){
        if(!::webView.isInitialized)return
        val p=getSharedPreferences("trip",MODE_PRIVATE); val d=JSONObject()
        d.put("lat",p.getString("lat","") ?: ""); d.put("lon",p.getString("lon","") ?: ""); d.put("speed",p.getFloat("speed",-1f)); d.put("accuracy",p.getFloat("accuracy",-1f)); d.put("distance",p.getFloat("distance_km",0f)); d.put("running",p.getBoolean("running",false))
        val t=JSONObject.quote(d.toString()); runOnUiThread{webView.evaluateJavascript("window.nativeGpsUpdate && window.nativeGpsUpdate($t);",null)}
    }
    inner class AndroidBridge { @JavascriptInterface fun startTrip(){runOnUiThread{iniciarServicoGps()}}; @JavascriptInterface fun stopTrip(){runOnUiThread{pararServicoGps()}}; @JavascriptInterface fun resetAll(){resetAplicativoNativo()}; @JavascriptInterface fun isNative():Boolean=true }
    override fun onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy()}
}
