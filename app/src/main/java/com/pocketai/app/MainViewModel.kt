package com.pocketai.app
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arm.aichat.ChatTurn
import com.arm.aichat.PocketLlama
import com.pocketai.app.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UiState(val conversations:List<Conversation> = emptyList(),val messages:List<Message> = emptyList(),val models:List<LocalModel> = emptyList(),val selectedModel:LocalModel?=null,val activeConversation:String?=null,val loadedPath:String?=null,val generating:Boolean=false,val error:String?=null,val settings:AppSettings=AppSettings())
class MainViewModel(app:Application):AndroidViewModel(app){private val a=app as PocketAiApp;private val engine=PocketLlama.get(app);private val active=MutableStateFlow<String?>(null);private val busy=MutableStateFlow(false);private val err=MutableStateFlow<String?>(null);private val loaded=MutableStateFlow<String?>(null);private var job:Job?=null
 private val msgs=active.flatMapLatest{if(it==null)flowOf(emptyList())else a.chats.messages(it)}
 val state=combine(a.chats.conversations(),msgs,a.models.models,a.models.selected,active,busy,err,a.settings.flow,loaded){ x: Array<Any?> -> UiState(conversations=x[0] as List<Conversation>,messages=x[1] as List<Message>,models=x[2] as List<LocalModel>,selectedModel=x[3] as LocalModel?,activeConversation=x[4] as String?,loadedPath=x[8] as String?,generating=x[5] as Boolean,error=x[6] as String?,settings=x[7] as AppSettings) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),UiState())
 fun open(id:String){active.value=id} fun newChat()=viewModelScope.launch{active.value=a.chats.create().id}
 fun deleteChat(id:String)=viewModelScope.launch{a.chats.delete(id);if(active.value==id)active.value=null}
 fun clearChats()=viewModelScope.launch{a.chats.clear();active.value=null}
 fun importModel(u:Uri)=viewModelScope.launch{runCatching{a.models.import(u)}.onSuccess{a.models.select(it.id)}.onFailure{err.value=friendly(it)}}
 fun selectModel(id:String)=viewModelScope.launch{a.models.select(id)}
 fun deleteModel(m:LocalModel)=viewModelScope.launch{runCatching{a.models.delete(m)};loaded.value=engine.loadedPath}
 fun loadModel(m:LocalModel)=viewModelScope.launch{busy.value=true;err.value=null;runCatching{engine.load(m.path,state.value.settings.contextSize);loaded.value=m.path;a.models.select(m.id)}.onFailure{err.value="Model kon niet worden geladen. Het bestand kan beschadigd, incompatibel of te groot voor het beschikbare geheugen zijn."};busy.value=false}
 fun unload()=viewModelScope.launch{engine.unload();loaded.value=null}
 fun send(text:String){if(text.isBlank()||busy.value)return;job=viewModelScope.launch{var cid=active.value;if(cid==null){cid=a.chats.create().id;active.value=cid};if(engine.loadedPath==null){err.value="Laad eerst een GGUF-model.";return@launch};val user=a.chats.add(cid!!,"user",text.trim());val assistant=a.chats.add(cid,"assistant","");busy.value=true;err.value=null;val s=state.value.settings;val turns=state.value.messages.filter{it.id!=assistant.id}.map{ChatTurn(it.role,it.content)}+ChatTurn("user",user.content);val all=listOf(ChatTurn("system",s.systemPrompt))+turns;val out=StringBuilder();runCatching{engine.generate(all,s.maxTokens,s.temperature,s.topP,s.topK).collect{out.append(it);a.chats.update(assistant.id,out.toString())}}.onFailure{if(it !is kotlinx.coroutines.CancellationException)err.value=friendly(it)};busy.value=false}}
 fun stop(){job?.cancel();busy.value=false}
 fun regenerate(){val m=state.value.messages.lastOrNull{it.role=="user"}?:return;send(m.content)}
 fun saveSettings(s:AppSettings)=viewModelScope.launch{a.settings.save(s)};fun resetSettings()=viewModelScope.launch{a.settings.reset()};fun dismissError(){err.value=null}
 private fun friendly(t:Throwable)=when(t){is OutOfMemoryError->"Onvoldoende RAM om dit model te laden.";else->t.message?:"Er is een lokale fout opgetreden."}
}
