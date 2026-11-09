package com.hoshizora.notifier.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.hoshizora.notifier.data.AppDatabase
import com.hoshizora.notifier.data.LocationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditLocationScreen(
    locationId: Long,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getInstance(context).locationDao() }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var name by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var alt by remember { mutableStateOf("0") }
    var existing by remember { mutableStateOf<LocationEntity?>(null) }

    // ジオコーディング検索State
    var searchQuery by remember { mutableStateOf("") }
    var isGeocodingSearching by remember { mutableStateOf(false) }
    var geocodeResults by remember { mutableStateOf<List<Address>>(emptyList()) }
    var showGeocodeDialog by remember { mutableStateOf(false) }

    // マップピッカーモーダルState
    var showMapPicker by remember { mutableStateOf(false) }

    // 現在地取得State
    var isFetchingCurrentLocation by remember { mutableStateOf(false) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    LaunchedEffect(locationId) {
        if (locationId >= 0) {
            val loc = dao.getAll().firstOrNull { it.id == locationId }
            if (loc != null) {
                existing = loc
                name = loc.name
                lat = loc.lat.toString()
                lon = loc.lon.toString()
                alt = loc.alt.toString()
            }
        }
    }

    // 現在地を取得してフォームに入力する関数
    fun fetchLocationForForm() {
        isFetchingCurrentLocation = true
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc: android.location.Location? ->
                if (loc != null) {
                    lat = String.format(Locale.US, "%.6f", loc.latitude)
                    lon = String.format(Locale.US, "%.6f", loc.longitude)
                    if (loc.hasAltitude()) {
                        alt = loc.altitude.toInt().toString()
                    }
                    if (name.isBlank()) {
                        name = "現在地"
                    }
                    isFetchingCurrentLocation = false
                    Toast.makeText(context, "現在地を入力しました", Toast.LENGTH_SHORT).show()
                } else {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        null
                    ).addOnSuccessListener { newLoc: android.location.Location? ->
                        isFetchingCurrentLocation = false
                        if (newLoc != null) {
                            lat = String.format(Locale.US, "%.6f", newLoc.latitude)
                            lon = String.format(Locale.US, "%.6f", newLoc.longitude)
                            if (newLoc.hasAltitude()) {
                                alt = newLoc.altitude.toInt().toString()
                            }
                            if (name.isBlank()) {
                                name = "現在地"
                            }
                            Toast.makeText(context, "現在地を入力しました", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "現在地の取得に失敗しました", Toast.LENGTH_SHORT).show()
                        }
                    }.addOnFailureListener {
                        isFetchingCurrentLocation = false
                        Toast.makeText(context, "現在地の取得に失敗しました", Toast.LENGTH_SHORT).show()
                    }
                }
            }.addOnFailureListener {
                isFetchingCurrentLocation = false
                Toast.makeText(context, "現在地の取得に失敗しました", Toast.LENGTH_SHORT).show()
            }
        } catch (_: SecurityException) {
            isFetchingCurrentLocation = false
            Toast.makeText(context, "位置情報の権限が必要です", Toast.LENGTH_SHORT).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fine || coarse) {
            fetchLocationForForm()
        } else {
            Toast.makeText(context, "位置情報の権限が拒否されました", Toast.LENGTH_SHORT).show()
        }
    }

    fun onRequestCurrentLocation() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            fetchLocationForForm()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // ジオコーディング検索実行
    fun performGeocodeSearch() {
        if (searchQuery.isBlank()) return
        keyboardController?.hide()
        isGeocodingSearching = true
        scope.launch {
            val results = searchGeocode(context, searchQuery)
            isGeocodingSearching = false
            if (results.isEmpty()) {
                Toast.makeText(context, "「$searchQuery」に一致する場所が見つかりませんでした", Toast.LENGTH_SHORT).show()
            } else if (results.size == 1) {
                val addr = results[0]
                lat = String.format(Locale.US, "%.6f", addr.latitude)
                lon = String.format(Locale.US, "%.6f", addr.longitude)
                if (name.isBlank()) {
                    name = addr.featureName ?: searchQuery
                }
                Toast.makeText(context, "「${addr.featureName ?: searchQuery}」の位置を入力しました", Toast.LENGTH_SHORT).show()
            } else {
                geocodeResults = results
                showGeocodeDialog = true
            }
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (locationId >= 0) "地点を編集" else "地点を追加",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 案内カード
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "💡 登録方法",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "・施設名や住所から検索して自動セット\n・「地図からピンで選択」で直接マップから位置を指定\n・「現在地から入力」でワンタップ登録\n・下の数値入力欄で手入力・微調整も可能です",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 便利ツールボタン群 (ジオコーディング検索, 地図選択, 現在地)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "かんたん自動入力・検索",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    // ジオコーディング検索フォーム
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("施設名・住所名で検索") },
                        placeholder = { Text("例: 富士山5合目, 美ヶ原高原") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "クリア")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { performGeocodeSearch() })
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { performGeocodeSearch() },
                            enabled = searchQuery.isNotBlank() && !isGeocodingSearching,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isGeocodingSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("検索中...")
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("位置を検索")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 地図から選択ボタン
                        OutlinedButton(
                            onClick = { showMapPicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("地図でピン選択", fontSize = 13.sp)
                        }

                        // 現在地から入力ボタン
                        OutlinedButton(
                            onClick = { onRequestCurrentLocation() },
                            enabled = !isFetchingCurrentLocation,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isFetchingCurrentLocation) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("現在地から入力", fontSize = 13.sp)
                        }
                    }
                }
            }

            // 数値入力セクション（手入力・微調整用）
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "地点詳細情報 (手入力・調整)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("地点名") },
                        placeholder = { Text("例: 長野県 美ヶ原高原") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = lat,
                        onValueChange = { lat = it },
                        label = { Text("緯度 (lat)") },
                        placeholder = { Text("例: 36.2267") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = lon,
                        onValueChange = { lon = it },
                        label = { Text("経度 (lon)") },
                        placeholder = { Text("例: 138.1072") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = alt,
                        onValueChange = { alt = it },
                        label = { Text("標高 (alt, メートル, 任意)") },
                        placeholder = { Text("例: 2000") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Button(
                onClick = {
                    val latVal = lat.toDoubleOrNull()
                    val lonVal = lon.toDoubleOrNull()
                    val altVal = alt.toDoubleOrNull() ?: 0.0
                    if (name.isBlank() || (latVal == null) || (lonVal == null)) {
                        Toast.makeText(context, "地点名・緯度・経度を正しく入力してください", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        dao.upsert(
                            LocationEntity(
                                id = existing?.id ?: 0,
                                name = name,
                                lat = latVal,
                                lon = lonVal,
                                alt = altVal,
                            ),
                        )
                        onDone()
                    }
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    "保存",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }

    // ジオコーディング複数候補選択ダイアログ
    if (showGeocodeDialog) {
        AlertDialog(
            onDismissRequest = { showGeocodeDialog = false },
            title = { Text("検索結果の選択") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("該当する場所を選択してください:", style = MaterialTheme.typography.bodySmall)
                    geocodeResults.forEachIndexed { index, addr ->
                        val placeName = addr.featureName ?: addr.getAddressLine(0) ?: "候補 ${index + 1}"
                        val detail = "${addr.latitude.format(4)}, ${addr.longitude.format(4)}"
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    lat = String.format(Locale.US, "%.6f", addr.latitude)
                                    lon = String.format(Locale.US, "%.6f", addr.longitude)
                                    if (name.isBlank()) {
                                        name = placeName
                                    }
                                    showGeocodeDialog = false
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(placeName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGeocodeDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    // 地図タップピッカーダイアログ
    if (showMapPicker) {
        val defaultLat = lat.toDoubleOrNull() ?: 35.681236
        val defaultLon = lon.toDoubleOrNull() ?: 139.767125

        MapPickerModalDialog(
            initialLat = defaultLat,
            initialLon = defaultLon,
            onConfirm = { selectedLat, selectedLon ->
                lat = String.format(Locale.US, "%.6f", selectedLat)
                lon = String.format(Locale.US, "%.6f", selectedLon)
                showMapPicker = false
                Toast.makeText(context, "緯度・経度を決定しました", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showMapPicker = false }
        )
    }
}

private fun Double.format(digits: Int): String = String.format(Locale.US, "%.${digits}f", this)

private suspend fun searchGeocode(context: Context, query: String): List<Address> = withContext(Dispatchers.IO) {
    if (query.isBlank()) return@withContext emptyList()
    val geocoder = Geocoder(context, Locale.JAPAN)
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocationName(query, 5, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        continuation.resume(addresses)
                    }

                    override fun onError(errorMessage: String?) {
                        continuation.resume(emptyList())
                    }
                })
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocationName(query, 5) ?: emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * OpenStreetMap (Leaflet JS) を使用したタップ・ピン移動可能なモーダルマップピッカー
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapPickerModalDialog(
    initialLat: Double,
    initialLon: Double,
    onConfirm: (lat: Double, lon: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedLat by remember { mutableDoubleStateOf(initialLat) }
    var selectedLon by remember { mutableDoubleStateOf(initialLon) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ヘッダー
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "地図をタップしてピンを設置",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Clear, contentDescription = "閉じる")
                    }
                }

                HorizontalDivider()

                // WebView マップ
                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                                }
                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        view?.evaluateJavascript("if (window.map) { window.map.invalidateSize(); }", null)
                                    }
                                }

                                addJavascriptInterface(object {
                                    @Suppress("unused")
                                    @JavascriptInterface
                                    fun onLocationSelected(lat: Double, lon: Double) {
                                        post {
                                            selectedLat = lat
                                            selectedLon = lon
                                        }
                                    }
                                }, "AndroidMapBridge")

                                val html = """
                                    <!DOCTYPE html>
                                    <html>
                                    <head>
                                    <meta charset="utf-8" />
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                                    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.css" />
                                    <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.js"></script>
                                    <style>
                                      html, body { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background-color: #1a1a2e; }
                                      #map { width: 100%; height: 100%; position: absolute; top: 0; bottom: 0; left: 0; right: 0; }
                                      .custom-pin {
                                        width: 24px;
                                        height: 24px;
                                        background-color: #e94560;
                                        border: 3px solid #ffffff;
                                        border-radius: 50%;
                                        box-shadow: 0 0 8px rgba(0,0,0,0.5);
                                      }
                                    </style>
                                    </head>
                                    <body>
                                    <div id="map"></div>
                                    <script>
                                      function initMap() {
                                        if (typeof L === 'undefined') {
                                          setTimeout(initMap, 100);
                                          return;
                                        }
                                        try {
                                          var map = L.map('map', {
                                            zoomControl: true,
                                            attributionControl: false
                                          }).setView([$initialLat, $initialLon], 12);
                                          window.map = map;

                                          L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                                            maxZoom: 19
                                          }).addTo(map);

                                          var pinIcon = L.divIcon({
                                            className: 'custom-pin',
                                            iconSize: [24, 24],
                                            iconAnchor: [12, 12]
                                          });

                                          var marker = L.marker([$initialLat, $initialLon], {
                                            icon: pinIcon,
                                            draggable: true
                                          }).addTo(map);

                                          function updatePos(lat, lon) {
                                            if (window.AndroidMapBridge && window.AndroidMapBridge.onLocationSelected) {
                                              window.AndroidMapBridge.onLocationSelected(lat, lon);
                                            }
                                          }

                                          map.on('click', function(e) {
                                            var lat = e.latlng.lat;
                                            var lon = e.latlng.lng;
                                            marker.setLatLng(e.latlng);
                                            updatePos(lat, lon);
                                          });

                                          marker.on('dragend', function(e) {
                                            var position = marker.getLatLng();
                                            updatePos(position.lat, position.lng);
                                          });

                                          setTimeout(function() { map.invalidateSize(); }, 200);
                                          setTimeout(function() { map.invalidateSize(); }, 600);
                                        } catch(e) {
                                          console.error(e);
                                        }
                                      }

                                      if (document.readyState === 'complete' || document.readyState === 'interactive') {
                                        initMap();
                                      } else {
                                        document.addEventListener('DOMContentLoaded', initMap);
                                      }
                                    </script>
                                    </body>
                                    </html>
                                """.trimIndent()

                                loadDataWithBaseURL("https://cdnjs.cloudflare.com", html, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                HorizontalDivider()

                // 選択中座標の表示と決定ボタン
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "選択中の位置",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "緯度: ${selectedLat.format(5)}\n経度: ${selectedLon.format(5)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Button(
                        onClick = { onConfirm(selectedLat, selectedLon) },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("この位置に決定")
                    }
                }
            }
        }
    }
}
