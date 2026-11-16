plugins {

    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.keonhanh.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.keonhanh.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-tuan2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }
}
kotlin {
    jvmToolchain(17)
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // Tuần 4: thư viện dùng chung (PR tuan4/hao-thu-vien). Cần thêm gì thì báo Phong Hào.
    implementation(libs.play.services.location)      // GPS: khoảng cách ở Khám phá (Hoàng), check-in (Khoa)
    implementation(libs.play.services.code.scanner)  // Quét QR mã mời, không cần quyền camera (Hào)
    implementation(libs.zxing.core)                  // Tạo ảnh QR mã mời (Hào)
    implementation(libs.androidx.work.runtime.ktx)   // Thông báo nền, nhắc giờ kèo (Hào)
    implementation(libs.retrofit.core)               // Gọi API Nominatim (Hoàng)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)              // Xem request, response trong Logcat khi debug
    implementation(libs.osmdroid)                    // Bản đồ OpenStreetMap (Hoàng, ưu tiên 2)
}
