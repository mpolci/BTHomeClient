package io.github.mpolci.bthomeclient.domain.model

@JvmInline
value class SensorAddress private constructor(val value: String) {
    companion object {
        private val addressPattern = Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$")

        fun from(value: String): SensorAddress? {
            val normalized = value.trim().uppercase().replace('-', ':')
            return normalized.takeIf(addressPattern::matches)?.let(::SensorAddress)
        }

        fun require(value: String): SensorAddress = requireNotNull(from(value)) {
            "A Bluetooth address must contain six hexadecimal octets."
        }
    }
}

