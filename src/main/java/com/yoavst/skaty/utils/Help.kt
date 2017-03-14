package com.yoavst.skaty.utils

import com.yoavst.skaty.model.Exclude
import com.yoavst.skaty.protocols.IProtocolMarker
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation

object Help : BasePrinter() {
    var ParameterNameColor: Color = Color.BLACK
    var FieldColor: Color = Color.RED
    var DefaultValueColor: Color = Color.CYAN


    fun generate(protocol: IProtocolMarker<*>): String {
        val defaultValue = protocol.defaultValue
        val properties = defaultValue::class.declaredMemberProperties.filter { it.findAnnotation<Exclude>() == null && it.name != "payload" && it.name != "marker" }
        val info = properties.map { Triple(it.name, it.returnType.format(), it.getFormatter().format(it.getter.call(defaultValue))) }
        val maxNameLen = info.maxBy { (name, _, _) -> name.length }!!.first.length + 1
        val maxTypeLen = info.maxBy { (_, type, _) -> type.length }!!.second.length + 1
        return info.joinToString(separator = "\n") { (name, type, value) ->
            "${name.padEnd(maxNameLen).colorize(ParameterNameColor)} : ${type.padEnd(maxTypeLen).colorize(FieldColor)} = (${value.colorize(DefaultValueColor)})"
        }

    }

    private fun KType.format(): String = toString().cleanTypeName()

    private fun String.cleanTypeName(): String {
        val indexGenerics = indexOf('<')
        if (indexGenerics < 0) {
            // no generics
            val index = lastIndexOf('.')
            if (index < 0) return this
            else return substring(index + 1)
        } else {
            val index = lastIndexOf('.', startIndex = indexGenerics)
            if (index < 0) return substring(0, indexGenerics) + "<" + substring(indexGenerics + 1, length - 1).cleanTypeName() + ">"
            else return substring(index + 1, indexGenerics) + "<" + substring(indexGenerics + 1, length - 1).cleanTypeName() + ">"
        }
    }
}