package one.nexus.crypto.tls;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import one.nexus.crypto.CryptoSuite;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Issues local SM2 / SM3withSM2 certificates using {@link CryptoSuite#GM}
 * algorithms. Not a CA product and not an HSM.
 */
final class GmTlsCertificates {
    private static final SecureRandom RANDOM = new SecureRandom();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private GmTlsCertificates() {}

    static KeyPair sm2() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", CryptoSuite.GM.provider());
            kpg.initialize(new ECGenParameterSpec(CryptoSuite.GM.curve()), RANDOM);
            return kpg.generateKeyPair();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("unable to generate SM2 key pair", ex);
        }
    }

    static X509Certificate issue(
            X509Certificate issuerCert,
            KeyPair issuer,
            KeyPair subject,
            String distinguishedName,
            boolean ca,
            int keyUsage,
            KeyPurposeId eku,
            boolean localhostSan) {
        try {
            Instant now = Instant.now();
            X500Name subjectName = new X500Name(distinguishedName);
            X500Name issuerName = issuerCert == null
                    ? subjectName
                    : new X500Name(issuerCert.getSubjectX500Principal().getName());
            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    issuerName,
                    new BigInteger(128, RANDOM),
                    Date.from(now.minus(1, ChronoUnit.HOURS)),
                    Date.from(now.plus(30, ChronoUnit.DAYS)),
                    subjectName,
                    subject.getPublic());
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(keyUsage));
            if (eku != null) {
                builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(eku));
            }
            if (localhostSan) {
                builder.addExtension(
                        Extension.subjectAlternativeName,
                        false,
                        new GeneralNames(new GeneralName[] {
                            new GeneralName(GeneralName.dNSName, "localhost"),
                            new GeneralName(GeneralName.iPAddress, "127.0.0.1")
                        }));
            }
            ContentSigner signer = new JcaContentSignerBuilder(CryptoSuite.GM.signatureAlgorithm())
                    .setProvider(CryptoSuite.GM.provider())
                    .build(issuer.getPrivate());
            return new JcaX509CertificateConverter()
                    .setProvider(CryptoSuite.GM.provider())
                    .getCertificate(builder.build(signer));
        } catch (Exception ex) {
            throw new IllegalStateException("unable to issue SM2 certificate for " + distinguishedName, ex);
        }
    }
}
