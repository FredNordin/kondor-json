package com.ubertob.kondor.json

import com.ubertob.kondor.json.jsonnode.*
import com.ubertob.kondor.outcome.Outcome
import com.ubertob.kondor.outcome.asFailure
import com.ubertob.kondor.outcome.asSuccess
import com.ubertob.kondor.outcome.failIfNull

typealias MutableFieldMap = MutableMap<String, JsonNode>
typealias PropertySetter = (MutableFieldMap, NodePath) -> MutableFieldMap

sealed class JsonProperty<T> {
    abstract val propName: String
    abstract fun setter(value: T): PropertySetter
    abstract fun getter(wrapped: JsonNodeObject): JsonOutcome<T>
}

data class JsonParsingException(val error: JsonError) : RuntimeException()

data class JsonPropMandatory<T : Any, JN : JsonNode>(
    override val propName: String,
    val converter: JsonConverter<T, JN>
) : JsonProperty<T>() {

    override fun getter(wrapped: JsonNodeObject): Outcome<JsonError, T> =
        wrapped._fieldMap[propName]
            ?.let(converter::fromJsonNodeBase)
            ?.failIfNull { JsonPropertyError(wrapped._path, propName, "Found null for non-nullable") }
            ?: JsonPropertyError(
                wrapped._path,
                propName,
                "Not found key '$propName'. Keys found: [${wrapped._fieldMap.keys.joinToString()}]"
            ).asFailure()


    override fun setter(value: T): PropertySetter = { fm, path ->
        fm.apply {
            put(propName, converter.toJsonNode(value, NodePathSegment(propName, path)))
        }
    }
}


data class JsonPropOptional<T, JN : JsonNode>(
    override val propName: String,
    val converter: JsonConverter<T, JN>
) : JsonProperty<T?>() {

    override fun getter(wrapped: JsonNodeObject): Outcome<JsonError, T?> =
        wrapped._fieldMap[propName]
            ?.let(converter::fromJsonNodeBase)
            ?: null.asSuccess()


    override fun setter(value: T?): PropertySetter = { fm, path ->
        fm.apply {
            put(propName,
                value?.let { converter.toJsonNode(it, NodePathSegment(propName, path)) }
                    ?: JsonNodeNull(path)
            )
        }
    }

}

data class JsonPropMandatoryFlatten<T : Any>(
    override val propName: String,
    val converter: ObjectNodeConverter<T>,
    val parent: JAny<*>
) : JsonProperty<T>() {

    private val parentProperties = parent.getProperties().map { it.propName }

    override fun getter(wrapped: JsonNodeObject): Outcome<JsonError, T> =
        wrapped.removeFieldsFromParent()
            .let { JsonNodeObject(it, wrapped._path) }
            .let(converter::fromJsonNode)
            .failIfNull { JsonPropertyError(wrapped._path, propName, "Found null for non-nullable") }

    private fun JsonNodeObject.removeFieldsFromParent() =
        _fieldMap.filterKeys { key -> !parentProperties.contains(key) }

    override fun setter(value: T): PropertySetter = { fm, path ->
        fm.apply {
            fm.putAll(converter.toJsonNode(value, path)._fieldMap)
        }
    }

}
