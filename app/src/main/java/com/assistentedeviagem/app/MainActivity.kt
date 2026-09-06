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
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val LOCATION_REQUEST = 1001
        private const val NOTIFICATION_REQUEST = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mantém a tela ligada durante a viagem
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Cria o WebView
        webView = WebView(this)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.mediaPlaybackRequiresUserGesture = false

        webView.webViewClient = WebViewClient()

        // Conecta o JavaScript do HTML ao Android
        webView.addJavascriptInterface(AndroidBridge(), "AndroidGPS")

        setContentView(webView)

        // Abre o index.html
        webView.loadUrl("file:///android_asset/index.html")

        // Solicita permissões
        solicitarPermissoes()

        // Envia os dados do GPS para o HTML a cada 1 segundo
        handler.post(object : Runnable {
            override fun run() {
                enviarGpsParaPagina()
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun solicitarPermissoes() {

        val permissoes = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (
                checkSelfPermission(
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissoes.add(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }

            if (
                checkSelfPermission(
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissoes.add(
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
        }

        // Permissão de notificações no Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            if (
                checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissoes.add(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }

        if (permissoes.isNotEmpty()) {

            requestPermissions(
                permissoes.toTypedArray(),
                LOCATION_REQUEST
            )
        }
    }

    private fun temLocalizacao(): Boolean {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||

            checkSelfPermission(
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        } else {
            true
        }
    }

    // =========================================================
    // INICIAR GPS
    // =========================================================

    private fun iniciarServicoGps() {

        if (!temLocalizacao()) {

            Toast.makeText(
                this,
                "Permita a localização do aparelho e toque em Iniciar novamente.",
                Toast.LENGTH_LONG
            ).show()

            solicitarPermissoes()

            return
        }

        // Mantém a tela ligada
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val intent = Intent(
            this,
            LocationService::class.java
        )

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                startForegroundService(intent)

            } else {

                startService(intent)
            }

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Não foi possível iniciar o GPS: ${
                    e.message ?: "erro"
                }",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // =========================================================
    // PARAR GPS
    // =========================================================

    private fun pararServicoGps() {

        try {

            stopService(
                Intent(
                    this,
                    LocationService::class.java
                )
            )

        } catch (_: Exception) {
        }
    }

    // =========================================================
    // ENVIA GPS PARA O HTML
    // =========================================================

    private fun enviarGpsParaPagina() {

        if (!::webView.isInitialized) {
            return
        }

        val prefs = getSharedPreferences(
            "trip",
            MODE_PRIVATE
        )

        val dados = JSONObject()

        dados.put(
            "lat",
            prefs.getString("lat", "") ?: ""
        )

        dados.put(
            "lon",
            prefs.getString("lon", "") ?: ""
        )

        dados.put(
            "speed",
            prefs.getFloat(
                "speed",
                -1f
            )
        )

        dados.put(
            "accuracy",
            prefs.getFloat(
                "accuracy",
                -1f
            )
        )

        dados.put(
            "distance",
            prefs.getFloat(
                "distance_km",
                0f
            )
        )

        dados.put(
            "running",
            prefs.getBoolean(
                "running",
                false
            )
        )

        val texto = JSONObject.quote(
            dados.toString()
        )

        runOnUiThread {

            webView.evaluateJavascript(
                "window.nativeGpsUpdate && window.nativeGpsUpdate($texto);",
                null
            )
        }
    }

    // =========================================================
    // PONTE ANDROID ↔ JAVASCRIPT
    // =========================================================

    inner class AndroidBridge {

        @JavascriptInterface
        fun startTrip() {

            runOnUiThread {

                iniciarServicoGps()
            }
        }

        @JavascriptInterface
        fun stopTrip() {

            runOnUiThread {

                pararServicoGps()
            }
        }

        @JavascriptInterface
        fun isNative(): Boolean {

            return true
        }
    }

    // =========================================================
    // ENCERRAMENTO
    // =========================================================

    override fun onDestroy() {

        handler.removeCallbacksAndMessages(null)

        super.onDestroy()
    }
}
