package com.pocketai.app.data
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
private val Context.ds by preferencesDataStore("settings")
data class AppSettings(val systemPrompt:String="You are a helpful, neutral assistant.",val temperature:Float=.7f,val topP:Float=.9f,val topK:Int=40,val contextSize:Int=4096,val maxTokens:Int=1024,val onboardingDone:Boolean=false)
class SettingsStore(private val c:Context){
 private object K{val system=stringPreferencesKey("system");val temp=floatPreferencesKey("temp");val topP=floatPreferencesKey("top_p");val topK=intPreferencesKey("top_k");val ctx=intPreferencesKey("ctx");val max=intPreferencesKey("max");val onboard=booleanPreferencesKey("onboard")}
 val flow=c.ds.data.map{AppSettings(it[K.system]?:"You are a helpful, neutral assistant.",it[K.temp]?:.7f,it[K.topP]?:.9f,it[K.topK]?:40,it[K.ctx]?:4096,it[K.max]?:1024,it[K.onboard]?:false)}
 suspend fun save(s:AppSettings)=c.ds.edit{it[K.system]=s.systemPrompt;it[K.temp]=s.temperature;it[K.topP]=s.topP;it[K.topK]=s.topK;it[K.ctx]=s.contextSize;it[K.max]=s.maxTokens;it[K.onboard]=s.onboardingDone}
 suspend fun reset()=c.ds.edit{it.clear()}
}
