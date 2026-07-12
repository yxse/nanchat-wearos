package com.nanchat.app.crypto

import android.content.Context
import com.nanchat.app.R
import java.security.MessageDigest

/**
 * Converts between a 24-word mnemonic and the 32-byte legacy Nano seed, matching multi-nano-web's
 * `wallet.fromLegacyMnemonic` / `tools.deriveMnemonic`: the seed IS the entropy (no PBKDF2), the
 * mnemonic is just that entropy re-encoded with the standard BIP39 English wordlist plus an
 * 8-bit SHA-256 checksum (24 words * 11 bits = 256 entropy bits + 8 checksum bits).
 */
object Bip39 {
    const val WORD_COUNT = 24

    private var words: List<String> = emptyList()

    fun init(context: Context) {
        if (words.isNotEmpty()) return
        words = context.resources.openRawResource(R.raw.bip39_english)
            .bufferedReader()
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun suggestions(prefix: String, max: Int = 5): List<String> {
        val p = prefix.trim().lowercase()
        if (p.isEmpty()) return emptyList()
        return words.filter { it.startsWith(p) }.take(max)
    }

    fun isKnownWord(word: String): Boolean = words.contains(word.trim().lowercase())

    fun mnemonicToSeed(mnemonicWords: List<String>): String {
        require(mnemonicWords.size == WORD_COUNT) { "Exactly 24 words are required" }
        val indices = mnemonicWords.map { w ->
            val idx = words.indexOf(w.trim().lowercase())
            require(idx >= 0) { "Unknown word: $w" }
            idx
        }
        val bits = indices.joinToString("") { it.toString(2).padStart(11, '0') }
        val entropyBits = bits.substring(0, 256)
        val providedChecksumBits = bits.substring(256)
        val entropyHex = bitsToHex(entropyBits)
        require(providedChecksumBits == checksumBits(entropyHex)) { "Invalid phrase (checksum mismatch)" }
        return entropyHex
    }

    fun seedToMnemonic(seedHex: String): List<String> {
        val bits = hexToBits(seedHex) + checksumBits(seedHex)
        return (0 until WORD_COUNT).map { i ->
            words[bits.substring(i * 11, i * 11 + 11).toInt(2)]
        }
    }

    private fun checksumBits(entropyHex: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(entropyHex.hexToBytes())
        return (hash[0].toInt() and 0xFF).toString(2).padStart(8, '0')
    }

    private fun hexToBits(hex: String): String =
        hex.hexToBytes().joinToString("") { b -> (b.toInt() and 0xFF).toString(2).padStart(8, '0') }

    private fun bitsToHex(bits: String): String {
        val bytes = ByteArray(bits.length / 8) { i -> bits.substring(i * 8, i * 8 + 8).toInt(2).toByte() }
        return bytes.toHex()
    }
}
