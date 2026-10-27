package com.ninjin.spotifyweb

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private const val AUTHOR = "ninjin0802"
private val Faint = Color(0x80FFFFFF)

/** 設定の「このアプリについて」。アイコンと版、作者のクレジット、リンク、注意書き */
@Composable
internal fun AboutPage(host: AppHost) {
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    var taps by remember { mutableIntStateOf(0) }
    val toast = remember { arrayOfNulls<Toast>(1) }
    val repo = "https://github.com/${Updater.REPO}"
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // アイコンを 10 回続けて押すと、デバッグモードを切り替える（7 回目から残りの回数を出す）
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF121212)).clickable(remember { MutableInteractionSource() }, null) {
            taps++
            val msg = when {
                taps >= 10 -> { taps = 0; host.debugMode = !host.debugMode
                    if (host.debugMode) tr("デバッグモードをオンにしました（設定に項目が出ます）", "Debug mode on (see Settings)") else tr("デバッグモードをオフにしました", "Debug mode off") }
                taps >= 7 -> tr("あと ${10 - taps} 回でデバッグモード${if (host.debugMode) "をオフ" else ""}", "${10 - taps} more taps to turn debug mode ${if (host.debugMode) "off" else "on"}")
                else -> null
            }
            if (msg != null) { toast[0]?.cancel(); toast[0] = Toast.makeText(context, msg, Toast.LENGTH_SHORT).also { it.show() } }
        }) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
        }
        Text("spotfy", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        Text(
            tr("バージョン ${BuildConfig.VERSION_NAME}", "Version ${BuildConfig.VERSION_NAME}"),
            color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp).clip(CircleShape).background(Accent.copy(alpha = 0.15f)).padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Text(
            tr("Spotify Web Player を、公式アプリのような操作感で", "The Spotify Web Player, with the feel of the official app"),
            color = Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, start = 24.dp, end = 24.dp),
        )
    }

    Section(tr("クレジット", "Credits"))
    // 作者のカード（GitHub のアイコンと名前。押すとプロフィールを開く）
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1F1F1F)).clickable { uri.openUri("https://github.com/$AUTHOR") }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage("https://github.com/$AUTHOR.png?size=128", null, Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF333333)))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(tr("作者", "Created by"), color = Sub, fontSize = 12.sp)
            Text(AUTHOR, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
            Text(tr("設計・開発・デザイン", "Design & development"), color = Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Text("GitHub ›", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }

    Section(tr("リンク", "Links"))
    SettingRow(tr("変更履歴", "Release notes"), tr("GitHub の Releases で見る", "View on GitHub Releases")) { uri.openUri("$repo/releases") }
    SettingRow(tr("ソースコード", "Source code"), repo.removePrefix("https://")) { uri.openUri(repo) }
    SettingRow(tr("ライセンス", "License"), "MIT License") { uri.openUri("$repo/blob/main/LICENSE") }

    Text(
        tr("これは非公式のアプリです。Spotify AB とは関係がなく、承認も受けていません。「Spotify」は Spotify AB の商標です。曲は端末に保存しません。",
            "This is an unofficial app, not affiliated with or endorsed by Spotify AB. \"Spotify\" is a trademark of Spotify AB. Nothing is saved to your device."),
        color = Faint, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
    )
    Text("© 2026 $AUTHOR", color = Faint, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
}
