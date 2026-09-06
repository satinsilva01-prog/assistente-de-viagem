package com.assistentedeviagem.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var webView: WebView

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
        private const val NOTIFICATION_PERMISSION_REQUEST = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        webView = WebView(this)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.webViewClient = WebViewClient()

        webView.addJavascriptInterface(AndroidGPS(), "AndroidGPS")

        setContentView(webView)

        webView.loadUrl("file:///android_asset/index.html")

        solicitarPermissoes()
    }

    private fun solicitarPermissoes() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST
                )
            }
        }

        if (
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )
        }
    }

    private fun iniciarGPS() {

        val intent = Intent(this, LocationService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun pararGPS() {

        val intent = Intent(this, LocationService::class.java)
        stopService(intent)
    }

    inner class AndroidGPS {

        @android.webkit.JavascriptInterface
        fun start() {

            runOnUiThread {
                if (
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    iniciarGPS()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Permissão de localização necessária.",
                        Toast.LENGTH_LONG
                    ).show()

                    solicitarPermissoes()
                }
            }
        }

        @android.webkit.JavascriptInterface
        fun stop() {

            runOnUiThread {
                pararGPS()
            }
        }
    }

    fun enviarGPSParaPagina(
        latitude: Double,
        longitude: Double,
        velocidade: Double,
        precisao: Float,
        distancia: Double
    ) {

        val json = """
            {
                "latitude": $latitude,
                "longitude": $longitude,
                "velocidade": $velocidade,
                "precisao": $precisao,
                "distancia": $distancia
            }
        """.trimIndent()

        runOnUiThread {
            webView.evaluateJavascript(
                "window.nativeGpsUpdate($json);",
                null
            )
        }
    }
}
