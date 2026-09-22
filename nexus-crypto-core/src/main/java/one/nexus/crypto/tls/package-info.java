/**
 * G17 leftover: local GM dual-certificate TLS material, handshake proof, and
 * a two-port international+GM dual-stack probe.
 *
 * <p>Uses the same SM2 / SM3 / SM4 algorithms and Bouncy Castle provider as
 * {@link one.nexus.crypto.CryptoSuite#GM}, plus P-256 / SHA256withECDSA for the
 * international stack. This is not a CryptoPort AEAD path and is not a
 * classified / Nginx-Tongsuo / GM/T 0024 / HSM delivery.
 */
package one.nexus.crypto.tls;
