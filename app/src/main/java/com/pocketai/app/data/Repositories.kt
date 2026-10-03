package com.pocketai.app.data
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.arm.aichat.PocketLlama
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ChatRepository(private val dao:ChatDao){fun conversations()=dao.conversations();fun messages(id:String)=dao.messages(id);suspend fun create():Conversation{val n=System.currentTimeMillis();return Conversation(UUID.randomUUID().toString(),"New chat",n,n).also{dao.putConversation(it)}};suspend fun add(c:String,r:String,t:String)=Message(UUID.randomUUID().toString(),c,r,t,System.currentTimeMillis()).also{dao.putMessage(it)};suspend fun update(id:String,t:String)=dao.updateMessage(id,t);suspend fun delete(id:String)=dao.deleteConversation(id);suspend fun clear()=dao.clearChats()}
class ModelRepository(private val c:Context,private val dao:ModelDao){
 val models=dao.models();val selected=dao.selected();
 suspend fun import(uri:Uri):LocalModel=withContext(Dispatchers.IO){require(c.contentResolver.getType(uri)==null||uri.toString().lowercase().contains("gguf")||displayName(uri).lowercase().endsWith(".gguf")){"Select a .gguf file"};val name=displayName(uri);require(name.lowercase().endsWith(".gguf")){"Select a .gguf file"};val dir=File(c.filesDir,"models").apply{mkdirs()};val id=UUID.randomUUID().toString();val dst=File(dir,"$id.gguf");c.contentResolver.openInputStream(uri)?.use{i->dst.outputStream().use{i.copyTo(it)}}?:error("Cannot open selected file");dst.inputStream().use{val magic=ByteArray(4);require(it.read(magic)==4&&String(magic,Charsets.US_ASCII)=="GGUF"){dst.delete();"Invalid GGUF file"}};LocalModel(id,name,dst.absolutePath,dst.length(),null,null,System.currentTimeMillis()).also{dao.put(it)}}
 suspend fun select(id:String){dao.clearSelection();dao.select(id)}
 suspend fun delete(m:LocalModel){if(PocketLlama.get(c).loadedPath==m.path)PocketLlama.get(c).unload();File(m.path).delete();dao.delete(m.id)}
 private fun displayName(u:Uri):String{c.contentResolver.query(u,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())return it.getString(0)};return u.lastPathSegment?:"model.gguf"}
}
