package com.trumpinvestitions.core.network

import com.trumpinvestitions.core.network.dto.QuoteDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private var webSocket: WebSocket? = null
    
    private val _quotesFlow = MutableSharedFlow<QuoteDto>()
    val quotesFlow: Flow<QuoteDto> = _quotesFlow.asSharedFlow()
    
    private val _connectionStatus = MutableSharedFlow<Boolean>()
    val connectionStatus: Flow<Boolean> = _connectionStatus.asSharedFlow()
    
    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connectionStatus.tryEmit(true)
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val quote = json.decodeFromString<QuoteDto>(text)
                _quotesFlow.tryEmit(quote)
            } catch (e: Exception) {
                // Обработка ошибки парсинга
            }
        }
        
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            _connectionStatus.tryEmit(false)
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _connectionStatus.tryEmit(false)
        }
    }
    
    fun connect() {
        if (webSocket == null) {
            val request = Request.Builder()
                .url("wss://api.trumpinvestitions.com/ws/quotes") // Заменить на реальный URL
                .build()
            
            webSocket = okHttpClient.newWebSocket(request, webSocketListener)
        }
    }
    
    fun disconnect() {
        webSocket?.close(1000, "Disconnected")
        webSocket = null
    }
    
    fun reconnect() {
        disconnect()
        connect()
    }
}