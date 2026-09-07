package io.github.mpolci.bthomeclient.app

import io.github.mpolci.bthomeclient.domain.model.SensorAddress

object AppRoutes {
    const val Main = "main"
    const val Settings = "settings"
    const val Registration = "registration/{address}"
    const val Detail = "detail/{address}"
    const val HistoryOverview = "history"
    const val History = "history/{address}"

    fun registration(address: SensorAddress) = "registration/${token(address)}"
    fun detail(address: SensorAddress) = "detail/${token(address)}"
    fun history(address: SensorAddress) = "history/${token(address)}"

    fun address(argument: String?): SensorAddress? {
        val token = argument ?: return null
        if (!TOKEN.matches(token)) return null
        return SensorAddress.from(token.chunked(2).joinToString(":"))
    }

    private fun token(address: SensorAddress): String = address.value.replace(":", "")
    private val TOKEN = Regex("[0-9A-F]{12}")
}
