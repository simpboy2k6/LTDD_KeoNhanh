// File build cấp project: chỉ khai báo plugin, cấu hình thật nằm ở app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
}
