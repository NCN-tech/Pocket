package com.arm.aichat

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PocketLlama private constructor(context: Context) {
    private val mutex = Mutex()
    private val nativeLibDir = context.applicationInfo.nativeLibraryDir
    @Volatile var loadedPath: String? = null; private set
    @Volatile var backendInfo: String = "CPU"; private set

    init { System.loadLibrary("pocket_llama"); nativeInit(nativeLibDir) }

    suspend fun load(path: String, contextSize: Int, gpuLayers: Int = 0) = mutex.withLock {
        unloadNative()
        val rc = loadNative(path, contextSize.coerceIn(512, 32768), gpuLayers)
        if (rc != 0) throw ModelLoadException(rc)
        loadedPath = path
        backendInfo = systemInfoNative()
    }

    suspend fun unload() = mutex.withLock { unloadNative(); loadedPath = null }

    fun generate(messages: List<ChatTurn>, maxTokens: Int, temperature: Float, topP: Float, topK: Int): Flow<String> = flow {
        mutex.withLock {
            check(loadedPath != null) { "No model loaded" }
            val packed = pack(messages)
            val rc = beginNative(packed, maxTokens.coerceIn(1, 8192), temperature.coerceIn(0f, 2f), topP.coerceIn(0.05f, 1f), topK.coerceIn(0, 200))
            if (rc != 0) throw GenerationException(rc)
            try {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val token = nextNative() ?: break
                    if (token.isNotEmpty()) emit(token)
                }
            } catch (e: CancellationException) { abortNative(); throw e }
        }
    }.flowOn(Dispatchers.Default)

    private fun pack(messages: List<ChatTurn>): String = buildString {
        messages.forEach { append(it.role).append('\u001e').append(it.content.replace("\u001f", " ").replace("\u001e", " ")).append('\u001f') }
    }

    private external fun nativeInit(nativeLibDir: String)
    private external fun loadNative(path: String, contextSize: Int, gpuLayers: Int): Int
    private external fun beginNative(packedMessages: String, maxTokens: Int, temperature: Float, topP: Float, topK: Int): Int
    private external fun nextNative(): String?
    private external fun abortNative()
    private external fun unloadNative()
    private external fun systemInfoNative(): String

    companion object { @Volatile private var INSTANCE: PocketLlama? = null; fun get(context: Context) = INSTANCE ?: synchronized(this) { INSTANCE ?: PocketLlama(context.applicationContext).also { INSTANCE = it } } }
}
data class ChatTurn(val role: String, val content: String)
class ModelLoadException(val code: Int): Exception("Model could not be loaded (code $code)")
class GenerationException(val code: Int): Exception("Generation could not start (code $code)")
