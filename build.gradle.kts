plugins {
    id("com.android.application") version "9.4.1" apply false
    // AGP 9 から Kotlin は AGP に組み込み。アプリには適用せず、使う Kotlin の版を決めるためだけに置く
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
