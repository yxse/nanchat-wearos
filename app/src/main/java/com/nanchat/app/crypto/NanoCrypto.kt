package com.nanchat.app.crypto

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box
import com.rfksystems.blake2b.Blake2b
import com.rfksystems.blake2b.security.Blake2b512Digest
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveSpec
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import uk.oczadly.karl.jnano.model.NanoAccount
import uk.oczadly.karl.jnano.util.WalletUtil
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Computes Blake2b-512 but reports its JCA algorithm name as "SHA-512".
 *
 * net.i2p.crypto.eddsa's EdDSAParameterSpec constructor insists on resolving the hash algorithm via
 * `MessageDigest.getInstance(name)`, and EdDSAEngine later checks that name against the digest instance
 * we hand it. Registering a real "BLAKE2B-512" JCA provider (com.rfksystems.blake2b.security.Blake2bProvider)
 * crashes on Android: its Provider(String,String,String) constructor isn't present in the Android runtime's
 * java.security.Provider. "SHA-512" is always registered and has the same 64-byte digest length Ed25519
 * requires, and nothing here ever calls the real SHA-512 - it's just a label both sides agree on.
 */
private class NanoBlake2b512Digest : MessageDigest("SHA-512") {
    private val engine = Blake2b(512)

    override fun engineUpdate(input: Byte) {
        engine.update(input)
    }

    override fun engineUpdate(input: ByteArray, offset: Int, len: Int) {
        engine.update(input, offset, len)
    }

    override fun engineDigest(): ByteArray {
        val out = ByteArray(engine.digestSize)
        engine.digest(out, 0)
        return out
    }

    override fun engineReset() {
        engine.clearKey()
        engine.clearSalt()
        engine.reset()
    }
}

/**
 * Re-implements the crypto used by the NanChat phone app (multi-nano-web) so a NanChat identity
 * (a Nano account) can be used standalone on the watch:
 *  - Nano's Ed25519 variant hashes with Blake2b-512 instead of SHA-512 (same clamping/curve math).
 *  - Message content is encrypted with a NaCl box (curve25519-xsalsa20-poly1305) after converting
 *    the Ed25519 keys to Curve25519 (birational map for the public key, Blake2b-512 + clamp for
 *    the private scalar - identical to the Ed25519 key expansion above).
 */
data class NanoAccountKeys(val address: String, val privateKeyHex: String)

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun String.hexToBytes(): ByteArray {
    val clean = removePrefix("0x")
    return ByteArray(clean.length / 2) {
        ((Character.digit(clean[it * 2], 16) shl 4) + Character.digit(clean[it * 2 + 1], 16)).toByte()
    }
}

object NanoCrypto {

    private val curveSpec: EdDSANamedCurveSpec by lazy {
        val base = EdDSANamedCurveTable.ED_25519_CURVE_SPEC
        EdDSANamedCurveSpec("nano-blake2b", base.curve, "SHA-512", base.scalarOps, base.b)
    }

    private val sodium: LazySodiumAndroid by lazy { LazySodiumAndroid(SodiumAndroid()) }

    // SecureRandom() re-seeds itself from the OS entropy pool on every construction, which is by far
    // the most expensive part of a one-off nonce - a single shared instance (thread-safe per the JCA
    // contract) reseeds only once.
    private val secureRandom by lazy { SecureRandom() }

    // Encrypting/decrypting a message re-derives the Curve25519 form of both the local account's key
    // and the other party's key every call, even though a conversation reuses the same two keys for
    // every message. Both conversions are pure functions of their input, so they're safe to memoize
    // for the lifetime of the process.
    private val curvePrivateCache = ConcurrentHashMap<String, ByteArray>()
    private val curvePublicCache = ConcurrentHashMap<String, ByteArray>()

    // deriveAccount() does an EdDSA scalar/point multiplication - cheap once, but wasteful to redo
    // on every push notification decrypt (NanChatMessagingService) when it's always the same seed.
    private val accountCache = ConcurrentHashMap<String, NanoAccountKeys>()

    fun isValidSeed(seed: String): Boolean = seed.matches(Regex("^[0-9a-fA-F]{64}$"))

    /** Derives account 0 from a legacy (blake2b) seed, matching multi-nano-web's `wallet.generateLegacy`. */
    fun deriveAccount(seedHex: String, index: Int = 0): NanoAccountKeys =
        accountCache.getOrPut("$seedHex:$index") {
            val privKeyBytes = WalletUtil.deriveKeyFromSeed(seedHex.hexToBytes(), index)
            // NanoAccount.fromPrivateKey() would go through jnano's internal Ed25519Blake2b class, whose
            // static initializer registers Blake2bProvider and crashes on Android (see signMessage above).
            // Derive the public key ourselves instead, the same way, then build the account from raw bytes.
            val account = NanoAccount(derivePublicKeyBytes(privKeyBytes))
            NanoAccountKeys(address = account.toAddress(), privateKeyHex = privKeyBytes.toHex())
        }

    private fun derivePublicKeyBytes(privateKeyBytes: ByteArray): ByteArray {
        val h = NanoBlake2b512Digest().digest(privateKeyBytes)
        return EdDSAPrivateKeySpec(curveSpec, h).a.toByteArray()
    }

    /**
     * Signs an arbitrary UTF-8 message, matching multi-nano-web's `tools.sign`.
     *
     * `tools.sign` doesn't Ed25519-sign the message bytes directly: it first hashes them with a
     * plain (unkeyed) Blake2b-256 (`Signer.generateHash`), then Ed25519-Blake2b-signs that 32-byte
     * digest. Skipping this pre-hash produces a signature the server's `tools.verify` will reject.
     */
    fun signMessage(privateKeyHex: String, message: String): String {
        val messageHash = ByteArray(32)
        Blake2b(256).apply {
            val bytes = message.toByteArray(Charsets.UTF_8)
            update(bytes, 0, bytes.size)
            digest(messageHash, 0)
        }

        val h = NanoBlake2b512Digest().digest(privateKeyHex.hexToBytes())
        val engine = EdDSAEngine(NanoBlake2b512Digest())
        engine.initSign(EdDSAPrivateKey(EdDSAPrivateKeySpec(curveSpec, h)))
        return engine.signOneShot(messageHash).toHex()
    }

    /** Encrypts a message for `recipientAddress`, matching multi-nano-web's `box.encrypt`. */
    fun boxEncrypt(message: String, recipientAddress: String, myPrivateKeyHex: String): String {
        val recipientCurvePub = edPublicToCurvePublicCached(recipientAddress)
        val myCurvePriv = edPrivateToCurvePrivateCached(myPrivateKeyHex)

        val nonce = ByteArray(Box.NONCEBYTES)
        secureRandom.nextBytes(nonce)

        val messageBytes = message.toByteArray(Charsets.UTF_8)
        val cipherText = ByteArray(messageBytes.size + Box.MACBYTES)
        check(sodium.cryptoBoxEasy(cipherText, messageBytes, messageBytes.size.toLong(), nonce, recipientCurvePub, myCurvePriv)) {
            "Encryption failed"
        }
        return Base64.getEncoder().encodeToString(nonce + cipherText)
    }

    /** Decrypts a message from `senderAddress`, matching multi-nano-web's `box.decrypt`. */
    fun boxDecrypt(encryptedBase64: String, senderAddress: String, myPrivateKeyHex: String): String {
        val full = Base64.getDecoder().decode(encryptedBase64)
        val nonce = full.copyOfRange(0, Box.NONCEBYTES)
        val cipherText = full.copyOfRange(Box.NONCEBYTES, full.size)

        val senderCurvePub = edPublicToCurvePublicCached(senderAddress)
        val myCurvePriv = edPrivateToCurvePrivateCached(myPrivateKeyHex)

        val message = ByteArray(cipherText.size - Box.MACBYTES)
        check(sodium.cryptoBoxOpenEasy(message, cipherText, cipherText.size.toLong(), nonce, senderCurvePub, myCurvePriv)) {
            "Decryption failed"
        }
        return String(message, Charsets.UTF_8)
    }

    private fun edPublicToCurvePublicCached(address: String): ByteArray =
        curvePublicCache.getOrPut(address) { edPublicToCurvePublic(NanoAccount.parseAddress(address).publicKeyBytes) }

    private fun edPrivateToCurvePrivateCached(privateKeyHex: String): ByteArray =
        curvePrivateCache.getOrPut(privateKeyHex) { edPrivateToCurvePrivate(privateKeyHex.hexToBytes()) }

    private fun edPublicToCurvePublic(edPublicKeyBytes: ByteArray): ByteArray {
        val curvePub = ByteArray(32)
        check(sodium.convertPublicKeyEd25519ToCurve25519(curvePub, edPublicKeyBytes)) {
            "Invalid Ed25519 public key"
        }
        return curvePub
    }

    private fun edPrivateToCurvePrivate(edPrivateKeyBytes: ByteArray): ByteArray {
        val digest = Blake2b512Digest()
        digest.update(edPrivateKeyBytes, 0, edPrivateKeyBytes.size)
        val h = ByteArray(64)
        digest.digest(h, 0, h.size)
        val scalar = h.copyOf(32)
        scalar[0] = (scalar[0].toInt() and 0xf8).toByte()
        scalar[31] = (scalar[31].toInt() and 0x7f).toByte()
        scalar[31] = (scalar[31].toInt() or 0x40).toByte()
        return scalar
    }
}
