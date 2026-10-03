plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
 namespace="com.arm.aichat"; compileSdk=36; ndkVersion="29.0.14206865"
 defaultConfig { minSdk=33; ndk { abiFilters += "arm64-v8a" }; externalNativeBuild { cmake { arguments += listOf("-DANDROID_STL=c++_shared","-DGGML_NATIVE=OFF","-DGGML_OPENMP=OFF","-DGGML_LLAMAFILE=OFF","-DLLAMA_OPENSSL=OFF","-DLLAMA_BUILD_TESTS=OFF","-DLLAMA_BUILD_EXAMPLES=OFF","-DLLAMA_BUILD_SERVER=OFF") } } }
 externalNativeBuild { cmake { path=file("src/main/cpp/CMakeLists.txt"); version="3.31.6" } }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
}
dependencies { implementation("androidx.core:core-ktx:1.17.0"); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2") }
