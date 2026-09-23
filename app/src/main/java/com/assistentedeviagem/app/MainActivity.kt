package com.assistentedeviagem.app

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Rational
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

                        /* O limite da via fica dentro de Dados da viagem, sem alterar o HTML-fonte. */
                        var grid=document.querySelector('#tabViagem .trip-data-card .grid');
                        var limit=document.getElementById('limiteVelocidade');
                        if(grid && limit && !document.getElementById('nativeLimiteViaMetric')){
                          var m=document.createElement('div');
                          m.className='metric'; m.id='nativeLimiteViaMetric';
                          m.innerHTML='<span>Limite da via</span><b id="nativeLimiteVia">'+(limit.textContent||'-- km/h')+'</b><div class="future-space"></div>';
                          grid.insertBefore(m,grid.children[1]||null);
                          var sync=function(){var x=document.getElementById('nativeLimiteVia'); if(x&&limit)x.textContent=limit.textContent||'-- km/h';};
                          setInterval(sync,1000);
                        }

                        /* Backup nativo: WebView não trata blob: downloads de forma confiável. */
                        function nativeBackup(){
                          try{
                            var ls={};
                            for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);if(k)ls[k]=localStorage.getItem(k);}
                            var payload={schema:'assistente-viagem-backup-completo-v2',app:'Assistente de Viagem',appVersion:'V40',createdAt:new Date().toISOString(),localStorage:ls};
                            var name='backup-assistente-de-viagem-'+new Date().toISOString().slice(0,10)+'.json';
                            if(window.AndroidGPS&&typeof window.AndroidGPS.saveBackup==='function'){
                              var ok=window.AndroidGPS.saveBackup(name,JSON.stringify(payload,null,2));
                              var msg=document.getElementById('backupCompletoMsg')||document.getElementById('backupViagemMsg');
                              if(msg)msg.textContent=ok?'✅ Backup salvo na pasta Downloads.':'❌ Não foi possível salvar o backup.';
                              return ok;
                            }
                          }catch(e){console.error(e);}
                          return false;
                        }
                        window.__avNativeBackup=nativeBackup;
                        ['btnBackupCompleto','btnBackupViagem'].forEach(function(id){
                          var b=document.getElementById(id);
                          if(b&&!b.dataset.nativeBackupBound){
                            b.dataset.nativeBackupBound='1';
                            b.addEventListener('click',function(e){
                              if(nativeBackup()){e.preventDefault();e.stopImmediatePropagation();}
                            },true);
                          }
                        });

                        /* Garante o modo escuro também no APK, com acesso pelo menu. */
                        var menu=document.getElementById('avMenuPanel');
                        if(menu&&!document.getElementById('nativeThemeMenuBtn')){
                          var tb=document.createElement('button');
                          tb.id='nativeThemeMenuBtn'; tb.type='button';
                          tb.textContent=document.body.classList.contains('dark')?'☀️ Modo claro':'🌙 Modo escuro';
                          tb.onclick=function(){
                            document.body.classList.toggle('dark');
                            var dark=document.body.classList.contains('dark');
                            localStorage.setItem('av_theme',dark?'dark':'light');
                            tb.textContent=dark?'☀️ Modo claro':'🌙 Modo escuro';
                            var header=document.getElementById('avThemeBtn');if(header)header.textContent=dark?'☀️':'🌙';
                          };
                          menu.appendChild(tb);
                        }

                        /* Acesso explícito ao Picture-in-Picture durante uma viagem. */
                        if(menu&&!document.getElementById('nativePipBtn')){
                          var pb=document.createElement('button');
                          pb.id='nativePipBtn'; pb.type='button'; pb.textContent='🖼️ Picture-in-Picture';
                          pb.onclick=function(){
                            if(window.AndroidGPS&&typeof window.AndroidGPS.enterPip==='function')window.AndroidGPS.enterPip();
                            else alert('Picture-in-Picture está disponível somente no APK.');
                          };
                          menu.appendChild(pb);
                        }
                      }catch(e){console.error(e)}
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
                webView.evaluateJavascript("try{localStorage.clear();sessionStorage.clear();}catch(e){}") {
                    handler.postDelayed({
                        try { webView.loadUrl("file:///android_asset/index.html") }
                        catch (e: Exception) { Toast.makeText(this, "Erro ao recarregar após reset: ${e.message ?: "erro"}", Toast.LENGTH_LONG).show() }
                    }, 700)
                }
            } catch (e: Exception) { Toast.makeText(this, "Erro ao resetar dados nativos: ${e.message ?: "erro"}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun enviarGpsParaPagina(){
        if(!::webView.isInitialized)return
        val p=getSharedPreferences("trip",MODE_PRIVATE); val d=JSONObject()
        d.put("lat",p.getString("lat","") ?: ""); d.put("lon",p.getString("lon","") ?: ""); d.put("speed",p.getFloat("speed",-1f)); d.put("accuracy",p.getFloat("accuracy",-1f)); d.put("distance",p.getFloat("distance_km",0f)); d.put("running",p.getBoolean("running",false))
        val t=JSONObject.quote(d.toString()); runOnUiThread{webView.evaluateJavascript("window.nativeGpsUpdate && window.nativeGpsUpdate($t);",null)}
    }

    private fun entrarPipSeViagemAtiva() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || isInPictureInPictureMode) return
        val running = getSharedPreferences("trip", MODE_PRIVATE).getBoolean("running", false)
        if (!running) {
            Toast.makeText(this, "Inicie uma viagem para usar o Picture-in-Picture.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível ativar o Picture-in-Picture.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun salvarBackupDownloads(filename: String, content: String): Boolean {
        return try {
            val safeName = filename.replace(Regex("[^A-Za-z0-9._-]"), "_")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                try {
                    resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) } ?: throw Exception("stream indisponível")
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    true
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                val file = java.io.File(dir, safeName)
                file.writeText(content, Charsets.UTF_8)
                Toast.makeText(this, "Backup salvo em ${file.absolutePath}", Toast.LENGTH_LONG).show()
                true
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao salvar backup: ${e.message ?: "erro"}", Toast.LENGTH_LONG).show()
            false
        }
    }

    inner class AndroidBridge {
        @JavascriptInterface fun startTrip(){runOnUiThread{iniciarServicoGps()}}
        @JavascriptInterface fun stopTrip(){runOnUiThread{pararServicoGps()}}
        @JavascriptInterface fun resetAll(){resetAplicativoNativo()}
        @JavascriptInterface fun isNative():Boolean=true
        @JavascriptInterface fun enterPip(){runOnUiThread{entrarPipSeViagemAtiva()}}
        @JavascriptInterface fun saveBackup(filename:String, content:String):Boolean = salvarBackupDownloads(filename,content)
    }

    override fun onUserLeaveHint(){
        super.onUserLeaveHint()
        entrarPipSeViagemAtiva()
    }

    override fun onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy()}
}
