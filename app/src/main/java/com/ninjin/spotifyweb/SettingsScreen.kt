package com.ninjin.spotifyweb

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal val QUALITIES = listOf(
    Triple("auto", "自動", "通信状況に合わせて調整"),
    Triple("low", "低音質", "通信量を抑える"),
    Triple("normal", "標準", "通信量を少し抑える"),
    Triple("high", "高音質", "AAC 128 kbps（無料プランの Web Player の上限）"),
    Triple("very_high", "最高音質", "AAC 256 kbps（Spotify Premium のみ）"),
)

@Composable
internal fun SettingsScreen(bridge: Bridge, host: AppHost, user: Bridge.User?, onBack: () -> Unit) {
    var page by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = page != null) { page = null }
    Column(Modifier.fillMaxSize().background(Bg).clickable(remember { MutableInteractionSource() }, null) {}.windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(Icons.back, 24) { if (page != null) page = null else onBack() }
            Text(
                when (page) { "quality" -> "メディアの音質"; "playback" -> "再生"; "account" -> "アカウント"; "look" -> "コンテンツと表示"; else -> "設定" },
                color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp),
            )
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 160.dp)) {
            when (page) {
                null -> {
                    val missing by bridge.missing.collectAsState()
                    // Spotify の更新で使えなくなった機能があれば、いちばん上で知らせる（押すとログを送れる）
                    if (missing.isNotEmpty()) SettingRow(
                        "⚠ 一部の機能が使えません",
                        missing.joinToString("・") + "\nSpotify 側の更新が原因かもしれません。押すとデバッグログを共有できます",
                    ) { host.shareDebugLog() }
                    SettingRow("アカウント", user?.name ?: "") { page = "account" }
                    SettingRow("コンテンツと表示", "テーマカラー・背景") { page = "look" }
                    SettingRow("再生", "バックグラウンド再生・広告抑制・クロスフェード") { page = "playback" }
                    SettingRow("メディアの音質", "Wi-Fi・モバイルデータ通信でのストリーミング") { page = "quality" }
                    SettingRow("デバッグログを共有", "直近の広告の検出・ミュート・早送りなどの記録") { host.shareDebugLog() }
                    SettingRow("このアプリについて", "Spotify Web Player を使った非公式のアプリです。曲は端末に保存しません") {}
                }
                "account" -> {
                    SettingRow("ユーザー名", user?.name ?: "") {}
                    SettingRow("ログアウト", "このアプリからログアウトします") { host.logout() }
                }
                "look" -> {
                    Section("テーマカラー")
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Theme.accents.forEach { (key, color) ->
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(if (Theme.accent == color) Color.White else Color.Transparent).padding(3.dp)
                                    .clip(CircleShape).background(color).clickable { Theme.accent = color; host.setSetting("accent", key) },
                            )
                        }
                    }
                    SwitchRow("AMOLED ブラック", "背景を真っ黒にして電池を節約", Theme.amoled) { Theme.amoled = it; host.setSetting("amoled", it.toString()) }
                }
                "playback" -> {
                    var bg by remember { mutableStateOf(host.setting("background", "true") == "true") }
                    SwitchRow("バックグラウンド再生", "アプリを閉じても再生を続ける", bg) { bg = it; host.setSetting("background", it.toString()) }
                    var ads by remember { mutableStateOf(host.setting("adblock", "true") == "true") }
                    SwitchRow("広告をブロック", "広告マネージャーを停止し、広告再生をミュート・スキップする", ads) { ads = it; host.setSetting("adblock", it.toString()) }
                    var gl by remember { mutableStateOf(host.setting("gapless", "true") == "true") }
                    SwitchRow("ギャップレス再生", "曲と曲のあいだの無音をなくして、続けて再生する", gl) { gl = it; host.setSetting("gapless", it.toString()) }
                    // クロスフェード：公式アプリと同じく、オンにするとスライダーで 1〜12 秒を選ぶ
                    var xf by remember { mutableStateOf(host.setting("crossfade", "0").toIntOrNull() ?: 0) }
                    var xfLast by remember { mutableStateOf(if (xf > 0) xf else 5) }
                    SwitchRow("クロスフェード", "曲と曲のあいだを指定した秒数でフェードさせる", xf > 0) { on ->
                        xf = if (on) xfLast else 0; host.setSetting("crossfade", xf.toString())
                    }
                    if (xf > 0) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${xf} 秒", color = Color.White, fontSize = 14.sp, modifier = Modifier.width(48.dp))
                        Slider(
                            value = xf.toFloat(), valueRange = 1f..12f, steps = 10,
                            onValueChange = { xf = it.toInt().coerceIn(1, 12); xfLast = xf },
                            onValueChangeFinished = { host.setSetting("crossfade", xf.toString()) },
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Accent, inactiveTrackColor = Color(0x4DFFFFFF)),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                "quality" -> {
                    var wifi by remember { mutableStateOf(host.quality(false)) }
                    var cell by remember { mutableStateOf(host.quality(true)) }
                    var rate by remember { mutableStateOf<Int?>(null) }
                    LaunchedEffect(wifi, cell) { delay(300); rate = runCatching { bridge.bitrate() }.getOrNull() }
                    Section("Wi-Fi でのストリーミング")
                    QUALITIES.forEach { q -> RadioRow(q, wifi == q.first) { wifi = q.first; host.setSetting("quality", q.first) } }
                    Section("モバイルデータ通信でのストリーミング")
                    QUALITIES.forEach { q -> RadioRow(q, cell == q.first) { cell = q.first; host.setSetting("quality.cell", q.first) } }
                    Section("いまの状態")
                    SettingRow("接続", if (host.metered) "モバイルデータ通信" else "Wi-Fi") {}
                    SettingRow("いま再生中の音質", rate?.let { if (it > 0) "AAC ${it / 1000} kbps" else "不明（このスマホで再生中の曲がありません）" } ?: "確認中…") {}
                }
            }
        }
    }
}

@Composable
internal fun Section(t: String) {
    Text(t, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
internal fun SettingRow(title: String, detail: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, color = Color.White, fontSize = 16.sp)
        if (detail.isNotEmpty()) Text(detail, color = Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
internal fun SwitchRow(title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Text(detail, color = Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White))
    }
}

@Composable
internal fun RadioRow(q: Triple<String, String, String>, selected: Boolean, onClick: () -> Unit) {
    val premium = q.first == "very_high"
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !premium, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(q.second, color = if (premium) Color(0x66FFFFFF) else if (selected) Accent else Color.White, fontSize = 16.sp)
            Text(q.third, color = if (premium) Color(0x55B3B3B3) else Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(if (selected) Accent else Color.Transparent)
                .let { if (!selected) it.background(Color.Transparent) else it },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(9.dp).clip(CircleShape).background(Bg))
            else Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF7C7C7C)).padding(2.dp)) { Box(Modifier.fillMaxSize().clip(CircleShape).background(Bg)) }
        }
    }
}
