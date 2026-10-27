package com.hoshizora.notifier.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hoshizora.notifier.data.AppDatabase
import com.hoshizora.notifier.data.LocationEntity
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditLocationScreen(
    locationId: Long,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getInstance(context).locationDao() }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var alt by remember { mutableStateOf("0") }
    var existing by remember { mutableStateOf<LocationEntity?>(null) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (locationId >= 0) "地点を編集" else "地点を追加") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "緯度経度は、可能な限り少数まで正確に入力してください。"
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("地点名") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = lat,
                onValueChange = { lat = it },
                label = { Text("緯度 (lat)") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = lon,
                onValueChange = { lon = it },
                label = { Text("経度 (lon)") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = alt,
                onValueChange = { alt = it },
                label = { Text("標高 (alt, メートル, 任意)") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val latVal = lat.toDoubleOrNull()
                    val lonVal = lon.toDoubleOrNull()
                    val altVal = alt.toDoubleOrNull() ?: 0.0
                    if (name.isBlank() || latVal == null || lonVal == null) {
                        return@Button
                    }
                    scope.launch {
                        dao.upsert(
                            LocationEntity(
                                id = existing?.id ?: 0,
                                name = name,
                                lat = latVal,
                                lon = lonVal,
                                alt = altVal
                            )
                        )
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }
        }
    }
}
