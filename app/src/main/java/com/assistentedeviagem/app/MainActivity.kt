package com.assistentedeviagem.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
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

        webView.addJavascriptInterface(object {

            @JavascriptInterface
            fun startGPS() {
                runOnUiThread {
                    iniciarGPS()
                }
            }

            @JavascriptInterface
            fun stopGPS() {
                runOnUiThread {
                    pararGPS()
                }
            }

        }, "AndroidGPS")

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

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
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

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(
                this,
                "Permissão de localização necessária.",
                Toast.LENGTH_LONG
            ).show()

            solicitarPermissoes()
            return
        }

        val intent = Intent(this, LocationService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        Toast.makeText(
            this,
            "GPS iniciado",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun pararGPS() {

        val intent = Intent(this, LocationService::class.java)
        stopService(intent)

        Toast.makeText(
            this,
            "GPS parado",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun enviarGPSParaPagina(
        latitude: Double,
        longitude: Double,
        velocidade: Double,
        precisao: Float,
        distancia: Double
    ) {

        val javascript = """
            window.nativeGpsUpdate(
                $latitude,
                $longitude,
                $velocidade,
                $precisao,
                $distancia
            );
        """.trimIndent()

        runOnUiThread {
            webView.evaluateJavascript(javascript, null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
