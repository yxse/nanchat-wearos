package com.nanchat.app.ui.util

fun shortAddress(address: String): String {
    if (address.length <= 16) return address
    return address.take(11) + "…" + address.takeLast(4)
}
