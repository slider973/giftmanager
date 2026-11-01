package ch.jonathanlemaine.giftmanager.data.auth

import java.security.MessageDigest
import java.security.SecureRandom

private val secureRandom = SecureRandom()

/** Nonce aléatoire en hexadécimal ([byteCount] octets, donc 2 × [byteCount] caractères). */
fun randomNonce(byteCount: Int = 32): String {
    val bytes = ByteArray(byteCount).also(secureRandom::nextBytes)
    return bytes.toHex()
}

/** SHA-256 de [input] en hexadécimal minuscule : c'est ce hash que Google place dans le jeton. */
fun sha256Hex(input: String): String =
    MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).toHex()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
