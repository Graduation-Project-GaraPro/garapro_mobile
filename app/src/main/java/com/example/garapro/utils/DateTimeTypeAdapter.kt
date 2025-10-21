package com.example.garapro.utils

import com.google.gson.*
import org.joda.time.DateTime
import java.lang.reflect.Type

class DateTimeTypeAdapter : JsonSerializer<DateTime>, JsonDeserializer<DateTime> {

    // Khi gửi dữ liệu lên server (serialize)
    override fun serialize(
        src: DateTime?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return if (src == null) JsonNull.INSTANCE
        else JsonPrimitive(src.toString("yyyy-MM-dd'T'HH:mm:ss"))
    }

    // Khi nhận dữ liệu từ server (deserialize)
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): DateTime? {
        return try {
            json?.asString?.let { DateTime.parse(it) }
        } catch (e: Exception) {
            null
        }
    }
}
