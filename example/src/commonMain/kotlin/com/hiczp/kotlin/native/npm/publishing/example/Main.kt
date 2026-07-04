package com.hiczp.kotlin.native.npm.publishing.example

fun main(args: Array<String>) {
    println("kotlin-native-npm-publishing example")
    println("platform: ${platformName()}")
    println(args.joinToString(prefix = "args: "))
}

expect fun platformName(): String
