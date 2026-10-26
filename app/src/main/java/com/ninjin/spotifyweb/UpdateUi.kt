package com.ninjin.spotifyweb

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val currentVersion get() = tr("いまの版: ${BuildConfig.VERSION_NAME}", "Current version: ${BuildConfig.VERSION_NAME}")
private fun available(version: String) = tr("新しい版 $version があります", "Version $version is available")
private fun downloading(progress: Float) = (progress * 100).toInt().let { tr("ダウンロード中… $it%", "Downloading… $it%") }

/** リリースノート（CHANGELOG の節）。Markdown の記号は見出しと箇条書きだけ軽く整える */
private fun plainNotes(md: String) =
    md.lines().joinToString("\n") { it.replace(Regex("^#+\\s*"), "").replace(Regex("^[-*]\\s+"), "・") }

/** 設定の「アップデート」。いまの状態を出し、押すと確認・インストールする */
@Composable
internal fun UpdateRow(updater: Updater) {
    val state by updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    val status = when (val s = state) {
        Updater.State.Idle -> tr("押すと新しい版を確認します", "Tap to check for updates")
        Updater.State.Checking -> tr("確認中…", "Checking…")
        Updater.State.UpToDate -> tr("最新の版です", "You're up to date")
        is Updater.State.Available -> available(s.release.version)
        is Updater.State.Downloading -> downloading(s.progress)
        is Updater.State.Ready -> tr("押すと ${s.release.version} をインストールします", "Tap to install ${s.release.version}")
        is Updater.State.Failed -> s.message
    }
    SettingRow(tr("アップデート", "Updates"), "$currentVersion\n$status") {
        when (state) {
            is Updater.State.Ready -> updater.install()
            Updater.State.Checking, is Updater.State.Downloading -> {}
            else -> scope.launch { updater.check() }
        }
    }
}

/** 新しい版のお知らせ。「更新」で APK を落とし、終わったらインストーラーを開く */
@Composable
internal fun UpdateDialog(updater: Updater) {
    val state by updater.state.collectAsState()
    val (release, progress) = when (val s = state) {
        is Updater.State.Available -> s.release to null
        is Updater.State.Downloading -> s.release to s.progress
        else -> return
    }
    AlertDialog(
        onDismissRequest = { if (progress == null) updater.later(release) },
        title = { Text(available(release.version)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (progress != null) {
                    Text(downloading(progress))
                    LinearProgressIndicator(
                        progress = { progress }, color = Accent, trackColor = Color(0x4DFFFFFF),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                } else {
                    Text(currentVersion)
                    if (release.notes.isNotEmpty()) Text(plainNotes(release.notes), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = { if (progress == null) TextButton(onClick = { updater.download(release) }) { Text(tr("更新", "Update"), color = Accent) } },
        dismissButton = { if (progress == null) TextButton(onClick = { updater.later(release) }) { Text(tr("あとで", "Later"), color = Color.White) } },
        containerColor = Color(0xFF282828), titleContentColor = Color.White, textContentColor = Sub,
    )
}
