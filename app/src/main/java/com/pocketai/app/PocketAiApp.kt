package com.pocketai.app
import android.app.Application
import com.pocketai.app.data.*
class PocketAiApp:Application(){lateinit var db:PocketDb;lateinit var chats:ChatRepository;lateinit var models:ModelRepository;lateinit var settings:SettingsStore;override fun onCreate(){super.onCreate();db=PocketDb.create(this);chats=ChatRepository(db.chats());models=ModelRepository(this,db.models());settings=SettingsStore(this)}}
