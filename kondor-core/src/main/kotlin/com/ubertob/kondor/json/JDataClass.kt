package com.ubertob.kondor.json

import com.ubertob.kondor.json.jsonnode.FieldMap
import com.ubertob.kondor.json.jsonnode.JsonNodeObject
import com.ubertob.kondor.json.jsonnode.NodePath
import com.ubertob.kondor.outcome.Outcome
import kotlin.reflect.KClass

abstract class JDataClass<T : Any>(klazz: KClass<T>) : JAny<T>() {

    val clazz: Class<T> = klazz.java
    val constructor by lazy { clazz.constructors.first() }

    private val jsonProperties  by lazy { getProperties() }

    override fun JsonNodeObject.deserializeOrThrow(): T? = error("Deprecated method! Override fromFieldMap if necessary.")

    override fun fromFieldMap(fieldMap: FieldMap, path: NodePath): Outcome<JsonError, T> =
        tryFromNode(path) {
            val args: List<Any?> = jsonProperties.map { prop ->
                prop.getter(fieldMap, path).orThrow()
            }

            buildInstance(args)
        }

    private fun buildInstance(args: List<Any?>) = (@Suppress("UNCHECKED_CAST")
    constructor.newInstance(*args.toTypedArray()) as T)


    //using ksp to get info about the T parameter names and order
//        class A : Store<B, C> { }
//
//        So to get the type of B and C
//
//        val B = it.superTypes.first().resolve().arguments.first().type?.resolve()?.declaration
//        print(B?.qualifiedName?.asString())
//
//        val C = it.superTypes.first().resolve().arguments.elementAtOrNull(1)?.type?.resolve()?.declaration
//        print(C?.qualifiedName?.asString())

    //todo can we check that is a Kotlin data class? we can also compare constructors args and Json fields


//        println("properties map ${map.keys}") //json names
//        val args = mutableListOf<Any?>()
////        first translate all props in objects values, then pass to the constructor
//        val consParams = constructor.parameters
//        println("found ${consParams.size} cons params")
//
//        val consParamNames = consParams.map { it.annotatedType } //just arg1 arg2...
//        println("consParamNames $consParamNames")
//
//        for (param in consParams) {
//            val field = map[param.name]
//            println("cons param ${param.name}  $field")
//            args.add(field)
//        }


    //using asm we can create a unnamed class with a single method that deserialize json based on the converter fields and then get the method handler and call it here !!!
    //this work assuming the JConverter has fields in the same exact order then the data class constructor

//
//    override fun fromJson(json: String): JsonOutcome<T> =
//
//        JsonLexerEager(json).tokenize().bind {
//
//            val tp = TokensPath(it, NodePathRoot) //root??
// //asm generated method handler that know the tokens to expect and what to extract
//
//            tp.toObjectFields(getProperties())
//        }.transform {
//            val args = it.values
//            @Suppress("UNCHECKED_CAST")
//            constructor.newInstance(*args.toTypedArray()) as T
//
//        }


}