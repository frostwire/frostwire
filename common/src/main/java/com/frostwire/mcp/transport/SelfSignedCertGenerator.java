package com.frostwire.mcp.transport;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

public class SelfSignedCertGenerator {

    public static SSLContext createSSLContext(String hostname) {
        try {
            Path keystorePath = Files.createTempFile("frostwire-mcp-", ".jks");
            keystorePath.toFile().deleteOnExit();
            char[] password = "frostwire-mcp".toCharArray();

            String cn = (hostname.equals("0.0.0.0") || hostname.equals("127.0.0.1"))
                    ? "localhost" : hostname;

            String keytool = findKeytool();
            ProcessBuilder pb = new ProcessBuilder(
                    keytool, "-genkeypair",
                    "-alias", "frostwire-mcp",
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-validity", "365",
                    "-keystore", keystorePath.toString(),
                    "-storepass", new String(password),
                    "-keypass", new String(password),
                    "-dname", "CN=" + cn + ",O=FrostWire",
                    "-deststoretype", "JKS"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes());
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("keytool failed (exit " + exitCode + "): " + output);
            }

            KeyStore ks = KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream(keystorePath.toFile())) {
                ks.load(fis, password);
            }

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, password);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);

            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate self-signed certificate: " + e.getMessage(), e);
        }
    }

    private static String findKeytool() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            File bin = new File(javaHome, "bin");
            File keytool = new File(bin, "keytool");
            if (!keytool.exists() && File.separatorChar == '\\') {
                keytool = new File(bin, "keytool.exe");
            }
            if (keytool.exists() && keytool.canExecute()) {
                return keytool.getAbsolutePath();
            }
        }
        return "keytool";
    }
}
