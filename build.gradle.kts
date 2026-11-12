plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 自带 Kotlin 支持；这里只用来钉住 Kotlin 版本
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
