package com.pocketai.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
class MainActivity:ComponentActivity(){private val vm by viewModels<MainViewModel>();override fun onCreate(b:Bundle?){super.onCreate(b);enableEdgeToEdge();setContent{PocketTheme{PocketRoot(vm)}}};override fun onStop(){super.onStop();vm.stop()}}
