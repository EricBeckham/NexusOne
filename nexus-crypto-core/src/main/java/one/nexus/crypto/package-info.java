/**
 * CryptoPort dual suites and SecretPort. Business code must not call JCE or
 * Bouncy Castle directly.
 *
 * <p>International: AES-256-GCM / SHA-256 / ECDSA P-256 / HMAC-SHA256.
 * GM: SM4-GCM / SM3 / SM2 / HMAC-SM3. Suite is a deployment choice; a sealed
 * blob carries suite + key version and cannot be decrypted by the other suite.
 */
package one.nexus.crypto;
