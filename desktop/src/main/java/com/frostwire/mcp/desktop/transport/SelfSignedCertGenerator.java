package com.frostwire.mcp.desktop.transport;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;

public class SelfSignedCertGenerator {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public static SSLContext createSSLContext(String hostname) {
        try {
            String cn = (hostname.equals("0.0.0.0") || hostname.equals("127.0.0.1"))
                    ? "localhost" : hostname;

            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();

            long now = System.currentTimeMillis();
            Date notBefore = new Date(now - 60000);
            Date notAfter = new Date(now + 365L * 24 * 60 * 60 * 1000);

            X500Name issuer = new X500Name("CN=" + cn + ",O=FrostWire");
            BigInteger serial = BigInteger.valueOf(now);

            JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    issuer, serial, notBefore, notAfter, issuer, keyPair.getPublic());

            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
            certBuilder.addExtension(Extension.subjectKeyIdentifier, false,
                    extUtils.createSubjectKeyIdentifier(keyPair.getPublic()));
            certBuilder.addExtension(Extension.authorityKeyIdentifier, false,
                    extUtils.createAuthorityKeyIdentifier(keyPair.getPublic()));
            certBuilder.addExtension(Extension.basicConstraints, true,
                    new BasicConstraints(true));
            certBuilder.addExtension(Extension.keyUsage, false,
                    new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment | KeyUsage.dataEncipherment | KeyUsage.keyCertSign));
            certBuilder.addExtension(Extension.extendedKeyUsage, false,
                    new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));

            X509CertificateHolder certHolder = certBuilder.build(
                    new JcaContentSignerBuilder("SHA256withRSA")
                            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                            .build(keyPair.getPrivate()));

            X509Certificate cert = new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(certHolder);

            char[] password = "frostwire-mcp".toCharArray();
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(null, password);
            ks.setKeyEntry("frostwire-mcp", keyPair.getPrivate(), password,
                    new java.security.cert.Certificate[]{cert});

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, password);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate self-signed certificate: " + e.getMessage(), e);
        }
    }
}
