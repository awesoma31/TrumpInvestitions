package org.awesoma.trumpinvestitions.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@Serializable
private data class ErrorBody(
    val code: String = "",
    val message: String = "",
)

object ApiError {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** Преобразует любое исключение от Retrofit/OkHttp в человекочитаемое сообщение на русском. */
    fun parse(e: Throwable): String = when (e) {
        is HttpException -> {
            val raw = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            val body = raw?.let {
                runCatching { json.decodeFromString<ErrorBody>(it) }.getOrNull()
            }
            toHumanMessage(body?.code, body?.message)
        }
        is ConnectException, is UnknownHostException ->
            "Нет подключения к серверу. Проверьте интернет-соединение"
        is SocketTimeoutException ->
            "Сервер не отвечает. Попробуйте позже"
        else ->
            "Произошла ошибка. Попробуйте ещё раз"
    }

    private fun toHumanMessage(code: String?, serverMessage: String?): String = when (code) {
        "INSUFFICIENT_FUNDS"         -> "Недостаточно средств для покупки"
        "INSUFFICIENT_ASSETS"        -> "Недостаточно акций для продажи"
        "INSUFFICIENT_MARKET_VOLUME" -> "Недостаточная ликвидность на рынке"
        "INSUFFICIENT_BALANCE"       -> "Недостаточно средств на счёте"
        "USER_ALREADY_EXISTS"        -> "Пользователь с таким именем или email уже существует"
        "INVALID_CREDENTIALS"        -> "Неверный логин или пароль"
        "INVALID_REFRESH_TOKEN"      -> "Сессия истекла, войдите заново"
        "NOT_FOUND"                  -> "Запрошенный ресурс не найден"
        "CONFLICT"                   -> "Операция невозможна в текущем состоянии"
        "UPSTREAM_TIMEOUT"           -> "Сервис временно недоступен. Попробуйте позже"
        "UPSTREAM_UNAVAILABLE"       -> "Сервис недоступен. Попробуйте позже"
        "INTERNAL_ERROR"             -> "Внутренняя ошибка сервера"
        "VALIDATION_ERROR",
        "BAD_REQUEST"                -> humanizeValidationMessage(serverMessage)
        else                         -> humanizeValidationMessage(serverMessage)
    }

    private fun humanizeValidationMessage(msg: String?): String {
        if (msg == null) return "Проверьте правильность введённых данных"
        return when {
            msg.contains("password", ignoreCase = true) && msg.contains("8") ->
                "Пароль должен содержать не менее 8 символов"
            msg.contains("password", ignoreCase = true) && msg.contains("empty") ->
                "Введите пароль"
            msg.contains("username", ignoreCase = true) && (msg.contains("3") || msg.contains("length")) ->
                "Имя пользователя должно содержать от 3 до 64 символов"
            msg.contains("username", ignoreCase = true) && msg.contains("invalid") ->
                "Имя пользователя может содержать только буквы, цифры, точки, тире и подчёркивания"
            msg.contains("email", ignoreCase = true) ->
                "Введите корректный email адрес"
            msg.contains("quantity", ignoreCase = true) ->
                "Некорректное количество акций"
            else -> msg
        }
    }
}
