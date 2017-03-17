@file:Suppress("PackageDirectoryMismatch")

package com.yoavst.skaty.protocols

import com.yoavst.skaty.model.*
import com.yoavst.skaty.protocols.declarations.IProtocol
import com.yoavst.skaty.protocols.declarations.IProtocolMarker
import com.yoavst.skaty.protocols.declarations.Layer3
import com.yoavst.skaty.serialization.*
import com.yoavst.skaty.utils.ToString
import com.yoavst.skaty.utils.clearLeftBits
import mu.KLogging
import unsigned.*

data class IP(var version: Byte = 4,
              var ihl: Byte? = null,
              @property:Formatted(UByteHexFormatter::class) var tos: Ubyte = 0.ub,
              var ecn: ECN = ECN.NonECT,
              var len: Ushort? = null,
              @property:Formatted(UshortHexFormatter::class) var id: Ushort = 1.us,
              var flags: Flags<Flag>? = emptyFlags(),
              var ttl: Ubyte = 64.ub,
              @property:Formatted(Protocol::class) var proto: Ubyte? = 0.ub,
              @property:Formatted(UshortHexFormatter::class) var chksum: Ushort? = null,
              var src: Address? = null,
              var dst: Address = ip("127.0.0.1"),
              var options: Options<IPOption> = emptyOptions(),
              @property:Exclude private var _payload: IProtocol<*>? = null) : BaseProtocol<IP>(), Ether.Aware, Layer3 {

    override var payload: IProtocol<*>?
        get() = _payload
        set(value) {
            _payload = value
            (value as? Aware)?.onPayload(this)
        }

    override fun onPayload(ether: Ether) {
        ether.type = Ether.Type.IP
    }

    override fun toString(): String = ToString.generate(this)
    override val marker get() = Companion

    override fun headerSize(): Int {
        val bytes = options.sumBy {
            val length = it.length.toInt()
            if (length == 0) 1 else length
        }
        return 20 + bytes + (bytes % 4)
    }

    companion object : IProtocolMarker<IP>, KLogging() {
        override val name: String get() = "IP"
        override fun isProtocol(protocol: IProtocol<*>): Boolean = protocol is IP
        override val defaultValue: IP = IP()
        override fun of(reader: SimpleReader, serializationContext: SerializationContext): IP? = try {
            val firstByte = reader.readUbyte()
            val secondByte = reader.readUbyte()

            val version = (firstByte shr 4).toByte()
            val ihl = firstByte.clearLeftBits(4).toByte()
            val tos = secondByte shr 2
            val ecn = ECN.of(secondByte.clearLeftBits(6).toInt())

            val length = reader.readUshort()
            val id = reader.readUshort()

            val forthShort = reader.readUshort()
            val flagsByte = (forthShort shr 13)

            val flags = Flag.values().filter { it.value and flagsByte != 0 }

            val ttl = reader.readUbyte()
            val proto = reader.readUbyte()
            val chksum = reader.readUshort()
            val src = Address(reader.readUint())
            val dst = Address(reader.readUint())

            var optionsSize = ihl - 5
            val options = mutableListOf<IPOption>()
            while (optionsSize > 0) {
                val option = IPOption.of(reader, serializationContext) ?: break
                options += option
                if (option is IPOption.EndOfOptions)
                    break
                if (option is IPOption.NOP)
                    optionsSize -= 1
                else
                    optionsSize -= option.length
            }

            val ip = IP(version, ihl, tos, ecn, length, id, Flags(flags.toSet()), ttl, proto, chksum, src,
                    dst, Options(options))
            ip._payload = serializationContext.serialize(reader, ip)

            ip
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse the packet to a IP packet." }
            null
        }
    }

    interface Aware {
        fun onPayload(ip: IP)
    }

    //region Data objects
    enum class Flag(val value: Int) {
        Reserved(0x1),
        DF(0x2),
        MF(0x4)
    }

    enum class ECN(val value: Int) {
        NonECT(0),
        ECT1(1),
        ECT0(2),
        CE(3);

        companion object {
            fun of(value: Int) = values().first { it.value == value }
        }
    }

    data class Address(val raw: Uint) {
        override fun toString(): String = raw.toFormattedIpAddress()

        fun toByteArray() = bufferOf(raw)
    }

    object Protocol : Formatter<Ubyte> {
        val ICMP = 1.ub
        val IP = 4.ub
        val TCP = 6.ub
        val UDP = 17.ub
        val GRE = 47.ub

        var KnownFormats: MutableMap<Ubyte, String> = mutableMapOf(
                ICMP to "ICMP",
                IP to "IP",
                TCP to "TCP",
                UDP to "UDP",
                GRE to "GRE"
        )

        override fun format(value: Ubyte?): String = KnownFormats.getOrDefault(value ?: 0.ub, "$value")
    }
    //endregion


}