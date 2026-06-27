package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MinimalWhite
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Zinc400
import com.example.ui.theme.Zinc800
import com.example.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let { url ->
            webView?.loadUrl(url)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            val baseCacheDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache")
            java.io.File(baseCacheDir, "js").mkdirs()
            java.io.File(baseCacheDir, "wasm").mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val attributionContext = remember(context) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        context.createAttributionContext("default_attribution")
                    } else {
                        context
                    }
                }
                var isOffline by remember { mutableStateOf(!isNetworkAvailable(attributionContext)) }
                var loadingProgress by remember { mutableFloatStateOf(0f) }
                var canGoBack by remember { mutableStateOf(false) }
                var pageError by remember { mutableStateOf<String?>(null) }
                var rawLogs by remember { mutableStateOf("") }
                
                var webViewInstance by remember { mutableStateOf<WebView?>(null) }

                LaunchedEffect(Unit) {
                    var wasOffline = !isNetworkAvailable(attributionContext)
                    networkStatusFlow(attributionContext).collectLatest { online ->
                        isOffline = !online
                        if (online && wasOffline) {
                            webViewInstance?.reload()
                        }
                        wasOffline = !online
                    }
                }

                LaunchedEffect(webViewInstance) {
                    val webView = webViewInstance ?: return@LaunchedEffect
                    while (true) {
                        kotlinx.coroutines.delay(60 * 60 * 1000L)
                        val sharedPrefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                        val lastClearTime = sharedPrefs.getLong("last_cache_clear_time", 0L)
                        val currentTime = System.currentTimeMillis()
                        val cacheClearInterval = 24 * 60 * 60 * 1000L
                        
                        if (isNetworkAvailable(context) && (currentTime - lastClearTime > cacheClearInterval)) {
                            webView.clearCache(true)
                            try {
                                val baseCacheDir = java.io.File(context.cacheDir, "WebView/Default/HTTP Cache/Code Cache")
                                java.io.File(baseCacheDir, "js").mkdirs()
                                java.io.File(baseCacheDir, "wasm").mkdirs()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            sharedPrefs.edit().putLong("last_cache_clear_time", currentTime).apply()
                            webView.reload()
                        }
                    }
                }

                BackHandler(enabled = canGoBack && !isOffline) {
                    webViewInstance?.goBack()
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = DarkBackground,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkBackground)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            RngWebView(
                                url = intent?.dataString ?: "https://native.authsrng.xyz",
                                modifier = Modifier.fillMaxSize(),
                                isOffline = isOffline,
                                onOfflineStatusChanged = { offline ->
                                    isOffline = offline
                                },
                                onWebViewCreated = { webView ->
                                    webViewInstance = webView
                                    this@MainActivity.webView = webView
                                },
                                onLoadingStatusChanged = { _ -> },
                                onProgressChanged = { progress ->
                                    loadingProgress = progress / 100f
                                },
                                onHistoryChanged = { backAvailable ->
                                    canGoBack = backAvailable
                                },
                                onPageLoadError = { errorMsg ->
                                    pageError = errorMsg
                                },
                                onLogEvent = { log ->
                                    rawLogs = log
                                }
                            )
                            
                            if (loadingProgress > 0f && loadingProgress < 1f) {
                                LinearProgressIndicator(
                                    progress = { loadingProgress },
                                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                                    color = MinimalWhite,
                                    trackColor = DarkSurface
                                )
                                Text(
                                    text = rawLogs,
                                    color = MinimalWhite.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 9.sp,
                                    maxLines = 6,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                                )
                            }
                            
                            if (pageError != null) {
                                OfflineScreen(
                                    error = pageError,
                                    onRetry = {
                                        pageError = null
                                        webViewInstance?.reload()
                                    }
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = isOffline,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 48.dp)
                        ) {
                            Surface(
                                color = Zinc800.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "offline mode",
                                        tint = MinimalWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "offline mode (cached)",
                                        color = MinimalWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webView?.destroy()
        webView = null
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RngWebView(
    url: String,
    modifier: Modifier = Modifier,
    isOffline: Boolean,
    onOfflineStatusChanged: (Boolean) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onLoadingStatusChanged: (Boolean) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onHistoryChanged: (Boolean) -> Unit,
    onPageLoadError: (String) -> Unit,
    onLogEvent: (String) -> Unit
) {
    val context = LocalContext.current
    val attributionContext = remember(context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            context.createAttributionContext("default_attribution")
        } else {
            context
        }
    }
    
    AndroidView(
        factory = { ctx ->
            val viewContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                ctx.createAttributionContext("default_attribution")
            } else {
                ctx
            }
            WebView(viewContext).apply {
                val sharedPrefs = viewContext.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                val lastClearTime = sharedPrefs.getLong("last_cache_clear_time", 0L)
                val currentTime = System.currentTimeMillis()
                val cacheClearInterval = 24 * 60 * 60 * 1000L
                if (isNetworkAvailable(viewContext) && (currentTime - lastClearTime > cacheClearInterval)) {
                    clearCache(true)
                    try {
                        val baseCacheDir = java.io.File(viewContext.cacheDir, "WebView/Default/HTTP Cache/Code Cache")
                        java.io.File(baseCacheDir, "js").mkdirs()
                        java.io.File(baseCacheDir, "wasm").mkdirs()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    sharedPrefs.edit().putLong("last_cache_clear_time", currentTime).apply()
                }

                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                
                keepScreenOn = true
                
                val gestureDetector = android.view.GestureDetector(viewContext, object : android.view.GestureDetector.SimpleOnGestureListener() {
                    private val SWIPE_THRESHOLD = 50
                    private val SWIPE_VELOCITY_THRESHOLD = 50

                    override fun onDown(e: android.view.MotionEvent): Boolean {
                        return true
                    }

                    override fun onFling(
                        e1: android.view.MotionEvent?,
                        e2: android.view.MotionEvent,
                        velocityX: Float,
                        velocityY: Float
                    ): Boolean {
                        if (e1 != null) {
                            val diffX = e2.x - e1.x
                            val diffY = e2.y - e1.y
                            if (Math.abs(diffX) > Math.abs(diffY) * 0.8f && 
                                Math.abs(diffX) > SWIPE_THRESHOLD && 
                                Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                                performHapticFeedback(
                                    android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                                    @Suppress("DEPRECATION") android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                                )
                            }
                        }
                        return false
                    }
                })

                setOnTouchListener { _, event ->
                    gestureDetector.onTouchEvent(event)
                    false
                }
                
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 12; Pixel 6 Build/SD1A.210817.023; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/94.0.4606.71 Mobile Safari/537.36 Native App"
                
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                @Suppress("DEPRECATION")
                settings.databaseEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                
                android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                
                @Suppress("DEPRECATION")
                settings.setRenderPriority(WebSettings.RenderPriority.HIGH)
                
                settings.cacheMode = if (isOffline) WebSettings.LOAD_CACHE_ONLY else WebSettings.LOAD_DEFAULT
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    android.webkit.ServiceWorkerController.getInstance().serviceWorkerWebSettings.cacheMode = 
                        if (isOffline) WebSettings.LOAD_CACHE_ONLY else WebSettings.LOAD_DEFAULT
                }
                
                val logsList = mutableListOf<String>()
                fun addLog(msg: String) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        logsList.add(msg)
                        if (logsList.size > 8) logsList.removeAt(0)
                        onLogEvent(logsList.joinToString("\n"))
                    }
                }
                
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): android.webkit.WebResourceResponse? {
                        val requestUrl = request?.url?.toString() ?: return null
                        
                        addLog("FETCH: ${request?.url?.path ?: requestUrl}")
                        
                        // Explicitly check server connectivity for main frame requests
                        if (request.isForMainFrame && (requestUrl.startsWith("http://") || requestUrl.startsWith("https://"))) {
                            try {
                                val connection = java.net.URL(requestUrl).openConnection() as java.net.HttpURLConnection
                                connection.requestMethod = "HEAD"
                                connection.connectTimeout = 3000
                                connection.readTimeout = 3000
                                val responseCode = connection.responseCode
                                val isLive = responseCode in 200..399
                                
                                addLog("HEAD [${responseCode}]: isLive=$isLive")
                                
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    onOfflineStatusChanged(!isLive)
                                }
                            } catch (e: Exception) {
                                addLog("HEAD FAIL: ${e.message}")
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    onOfflineStatusChanged(true)
                                }
                            }
                        }
                        
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val requestUrl = request?.url ?: return false
                        val host = requestUrl.host ?: return false
                        
                        if (host.endsWith("authsrng.xyz")) {
                            return false
                        }
                        
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, requestUrl)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                        }
                        return true
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onLoadingStatusChanged(true)
                        onHistoryChanged(view?.canGoBack() == true)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onLoadingStatusChanged(false)
                        onHistoryChanged(view?.canGoBack() == true)
                    }

                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        onHistoryChanged(view?.canGoBack() == true)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        addLog("ERR: ${error?.errorCode} ${error?.description}")
                        if (request?.isForMainFrame == true) {
                            val errorLog = "Error Code: ${error?.errorCode}\nDescription: ${error?.description}\nURL: ${request.url}"
                            onPageLoadError(errorLog)
                        }
                    }
                    
                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: android.webkit.WebResourceResponse?
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        addLog("HTTP_ERR: ${errorResponse?.statusCode} ${request?.url?.path}")
                        if (request?.isForMainFrame == true) {
                            val errorLog = "HTTP Error: ${errorResponse?.statusCode}\nReason: ${errorResponse?.reasonPhrase}\nURL: ${request.url}"
                            onPageLoadError(errorLog)
                        }
                    }
                }
                
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        onProgressChanged(newProgress)
                    }
                }
                
                loadUrl(url)
                onWebViewCreated(this)
            }
        },
        update = { webView ->
            webView.settings.cacheMode = if (isOffline) WebSettings.LOAD_CACHE_ONLY else WebSettings.LOAD_DEFAULT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.webkit.ServiceWorkerController.getInstance().serviceWorkerWebSettings.cacheMode = 
                    if (isOffline) WebSettings.LOAD_CACHE_ONLY else WebSettings.LOAD_DEFAULT
            }
        },
        modifier = modifier
    )
}


fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

fun networkStatusFlow(context: Context): Flow<Boolean> = callbackFlow {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(true)
        }
        override fun onLost(network: Network) {
            trySend(false)
        }
    }
    
    try {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
    } catch (e: Exception) {
    }
    
    trySend(isNetworkAvailable(context))
    
    awaitClose {
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
        }
    }
}
