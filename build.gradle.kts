plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace="com.assistentedeviagem.app"
    compileSdk=35
    defaultConfig {
        applicationId="com.assistentedeviagem.app"
        minSdk=26
        targetSdk=35
        versionCode=2
        versionName="2.0"
    }
}
dependencies {
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
