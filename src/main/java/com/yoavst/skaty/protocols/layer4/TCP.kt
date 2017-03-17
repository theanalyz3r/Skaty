@file:Suppress("PackageDirectoryMismatch")

package com.yoavst.skaty.protocols

import com.yoavst.skaty.model.*
import com.yoavst.skaty.protocols.declarations.IProtocol
import com.yoavst.skaty.protocols.declarations.IProtocolMarker
import com.yoavst.skaty.protocols.declarations.Layer4
import com.yoavst.skaty.serialization.*
import com.yoavst.skaty.utils.ToString
import mu.KLogging
import unsigned.*

data class TCP(var sport: Ushort? = 20.us,
               var dport: Ushort? = 80.us,
               var seq: Uint = 0.ui,
               var ack: Uint = 0.ui,
               var dataofs: Byte? = null,
               var reserved: Byte = 0,
               var flags: Flags<Flag> = flagsOf(Flag.SYN),
               var window: Ushort = 8192.us,
               @property:Formatted(UshortHexFormatter::class) var chksum: Ushort? = null,
               var urgptr: Ushort = 0.us,
               var options: Options<TCPOption> = emptyOptions(),
               override var payload: IProtocol<*>? = null) : BaseProtocol<TCP>(), IP.Aware, Layer4 {
    override fun onPayload(ip: IP) {
        ip.proto = IP.Protocol.TCP
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

    companion object : IProtocolMarker<TCP>, KLogging() {
        override val name: String get() = "TCP"
        override fun isProtocol(protocol: IProtocol<*>): Boolean = protocol is TCP
        override val defaultValue: TCP = TCP()

        override fun of(reader: SimpleReader, serializationContext: SerializationContext): TCP? = try {
            val sport = reader.readUshort()
            val dport = reader.readUshort()
            val seq = reader.readUint()
            val ack = reader.readUint()

            val nextByte = reader.readUbyte()
            val headerLength = (nextByte shr 4).toByte()
            val reserved = ((nextByte shl 4) shr 5).toByte()

            val flags = mutableSetOf<Flag>()
            if ((nextByte and 1) != 0.ub) flags += Flag.NS

            val flagsByte = reader.readByte().toUInt()
            Flag.values().filterTo(flags) { it.value and flagsByte != 0 }
            val windowsSize = reader.readUshort()
            val checksum = reader.readUshort()
            val urgPtr = reader.readUshort()

            var optionsSize = (headerLength - 5) * 4
            val options = mutableListOf<TCPOption>()
            while (optionsSize > 0) {
                val option = TCPOption.of(reader, serializationContext) ?: break
                options += option
                if (option is TCPOption.EndOfOptions)
                    break
                if (option is TCPOption.NOP)
                    optionsSize -= 1
                else
                    optionsSize -= option.length
            }

            val tcp = TCP(sport, dport, seq, ack, headerLength, reserved, Flags(flags.toSet()), windowsSize, checksum,
                    urgPtr, Options(options))
            tcp.payload = serializationContext.serialize(reader, tcp)

            tcp
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse the packet to a TCP packet." }
            null
        }
    }

    //region Data objects
    enum class Flag(val value: Int) {
        FIN(0x01),
        SYN(0x02),
        RST(0x04),
        PSH(0x08),
        ACK(0x10),
        URG(0x20),
        ECE(0x40),
        CWR(0x80),
        NS(0x160)

    }
    //endregion
}


