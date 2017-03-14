package com.yoavst.skaty

import com.yoavst.skaty.model.flagsOf
import com.yoavst.skaty.protocols.*
import com.yoavst.skaty.protocols.TCP.Companion.optionsOf
import com.yoavst.skaty.protocols.TCP.Flag.ACK
import com.yoavst.skaty.protocols.TCP.Flag.SYN
import com.yoavst.skaty.protocols.TCP.Option.Companion.NOP
import com.yoavst.skaty.protocols.TCP.Option.Companion.timestamp
import com.yoavst.skaty.utils.Help
import unsigned.*

fun main(args: Array<String>) {
    val packet = Ether(src = mac("11-22-33-44-55-66")) /
            IP(dst = ip("192.168.1.1"), tos = 53.ub) /
            TCP(dport = 80.us, sport = 1200.us, flags = flagsOf(SYN, ACK), options = optionsOf(NOP(), timestamp(1489416311.ui, 1.ui)), chksum = 53432.us) /
            "Hello world"
    println(Help.generate(TCP))
    println()
    // work with properties
    packet.dst = mac("AA-BB-CC-DD-EE-FF")
    del(packet::dst)
    println(packet)

    // get a layer
    val tcp = packet[TCP]
    println(tcp.dport)
    println(UDP in packet)

    // send packet
    sendp(packet)
    val ip = packet[IP]
    send(ip)
    val response = sr1(ip, timeout = 2000)
    if (response != null) {
        println(response[Raw])
    }

    // sniff
    val packets = sniff().filter { TCP in it && it[TCP].dport == 1200.us }.timeout(2000).take(10).map { item -> item[TCP].ack }.toList()
    packets.forEach(::println)

}

fun sendp(packet: Layer2) {}
fun send(packet: Layer3) {}
fun sr1(packet: Layer3, timeout: Long = 1000): IProtocol<*>? = packet as IProtocol<*>
fun sniff(): Sequence<IProtocol<*>> = emptySequence()
fun <K : IProtocol<*>> Sequence<K>.timeout(timeout: Long): Sequence<K> = this

