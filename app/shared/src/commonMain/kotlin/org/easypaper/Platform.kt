package org.easypaper

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform