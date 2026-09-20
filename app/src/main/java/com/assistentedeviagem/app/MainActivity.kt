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
import android.webkit.WebChromeClient
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
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webView.evaluateJavascript("""
                    (function(){
                      try{
                        var lock=document.getElementById('viagemLockTag'); if(lock) lock.remove();
                        var shield=document.getElementById('viagemLockShield'); if(shield) shield.remove();
                        var panel=document.getElementById('av-trip-panel');
                        if(panel){panel.style.position='static';panel.style.top='auto';panel.style.zIndex='50';}
                        document.querySelectorAll('.viagem-scroll-locked').forEach(function(e){e.classList.remove('viagem-scroll-locked');});
                      }catch(e){}
                    })();
                """.trimIndent(), null)
            }
        }
        webView.addJavascriptInterface(AndroidBridge(), "AndroidGPS")
        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
        solicitarPermissoes()
        handler.post(object : Runnable {
            override fun run() {
                enviarGpsParaPagina()
                handler.postDelayed(this, 1000)
            }
        })
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
                Toast.makeText(this, "Resetando aplicativo...", Toast.LENGTH_SHORT).show()
                pararServicoGps()
                getSharedPreferences("trip", MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("app", MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("vehicle", MODE_PRIVATE).edit().clear().apply()
                webView.stopLoading()
                webView.clearHistory()
                webView.clearFormData()
                webView.clearCache(true)
                WebStorage.getInstance().deleteAllData()

                // A limpeza do WebView precisa terminar antes do reload.
                // O callback garante que localStorage/sessionStorage foram
                // limpos antes de carregar novamente o index.html.
                webView.evaluateJavascript("try{localStorage.clear();sessionStorage.clear();}catch(e){}") {
                    handler.postDelayed({
                        try {
                            webView.loadUrl("file:///android_asset/index.html")
                        } catch (e: Exception) {
                            Toast.makeText(this, "Erro ao recarregar após reset: ${e.message ?: "erro"}", Toast.LENGTH_LONG).show()
                        }
                    }, 700)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Erro ao resetar dados nativos: ${e.message ?: "erro"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enviarGpsParaPagina(){
        if(!::webView.isInitialized)return
        val p=getSharedPreferences("trip",MODE_PRIVATE); val d=JSONObject()
        d.put("lat",p.getString("lat","") ?: ""); d.put("lon",p.getString("lon","") ?: ""); d.put("speed",p.getFloat("speed",-1f)); d.put("accuracy",p.getFloat("accuracy",-1f)); d.put("distance",p.getFloat("distance_km",0f)); d.put("running",p.getBoolean("running",false))
        val t=JSONObject.quote(d.toString()); runOnUiThread{webView.evaluateJavascript("window.nativeGpsUpdate && window.nativeGpsUpdate($t);",null)}
    }

    inner class AndroidBridge {
        @JavascriptInterface fun startTrip(){runOnUiThread{iniciarServicoGps()}}
        @JavascriptInterface fun stopTrip(){runOnUiThread{pararServicoGps()}}
        @JavascriptInterface fun resetAll(){resetAplicativoNativo()}
        @JavascriptInterface fun isNative():Boolean=true
    }

    override fun onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy()}
}
