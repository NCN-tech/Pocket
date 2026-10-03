package com.pocketai.app.data
import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="conversations") data class Conversation(@PrimaryKey val id:String, val title:String, val createdAt:Long, val updatedAt:Long)
@Entity(tableName="messages", foreignKeys=[ForeignKey(entity=Conversation::class,parentColumns=["id"],childColumns=["conversationId"],onDelete=ForeignKey.CASCADE)], indices=[Index("conversationId")]) data class Message(@PrimaryKey val id:String,val conversationId:String,val role:String,val content:String,val createdAt:Long)
@Entity(tableName="models") data class LocalModel(@PrimaryKey val id:String,val name:String,val path:String,val sizeBytes:Long,val architecture:String?,val quantization:String?,val importedAt:Long,val isSelected:Boolean=false)
@Dao interface ChatDao {
 @Query("SELECT * FROM conversations ORDER BY updatedAt DESC") fun conversations():Flow<List<Conversation>>
 @Query("SELECT * FROM messages WHERE conversationId=:id ORDER BY createdAt ASC") fun messages(id:String):Flow<List<Message>>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putConversation(v:Conversation)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putMessage(v:Message)
 @Query("UPDATE messages SET content=:content WHERE id=:id") suspend fun updateMessage(id:String,content:String)
 @Query("DELETE FROM conversations WHERE id=:id") suspend fun deleteConversation(id:String)
 @Query("DELETE FROM conversations") suspend fun clearChats()
}
@Dao interface ModelDao {
 @Query("SELECT * FROM models ORDER BY importedAt DESC") fun models():Flow<List<LocalModel>>
 @Query("SELECT * FROM models WHERE isSelected=1 LIMIT 1") fun selected():Flow<LocalModel?>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun put(v:LocalModel)
 @Query("UPDATE models SET isSelected=0") suspend fun clearSelection()
 @Query("UPDATE models SET isSelected=1 WHERE id=:id") suspend fun select(id:String)
 @Query("DELETE FROM models WHERE id=:id") suspend fun delete(id:String)
}
@Database(entities=[Conversation::class,Message::class,LocalModel::class],version=1,exportSchema=false)
abstract class PocketDb:RoomDatabase(){abstract fun chats():ChatDao;abstract fun models():ModelDao;companion object{fun create(c:Context)=Room.databaseBuilder(c,PocketDb::class.java,"pocket-ai.db").fallbackToDestructiveMigration(false).build()}}
