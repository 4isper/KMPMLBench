package com.m4isper.kmpmlbench

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform