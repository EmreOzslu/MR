import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/**
 * APK imzalama ve doğrulama (apksig). Android SDK build-tools içindeki apksigner'ın yerine geçer.
 *
 *   sign   <keystore> <alias> <storepass> <in.apk> <out.apk>
 *   verify <apk>
 */
public class SignApk {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("verify")) {
            ApkVerifier.Result r = new ApkVerifier.Builder(new File(args[1])).build().verify();
            System.out.println("doğrulandı=" + r.isVerified()
                    + " v1=" + r.isVerifiedUsingV1Scheme()
                    + " v2=" + r.isVerifiedUsingV2Scheme());
            for (Object e : r.getErrors()) System.out.println("HATA: " + e);
            if (!r.isVerified()) System.exit(1);
            return;
        }

        String alias = args[2];
        char[] pass = args[3].toCharArray();
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        try (InputStream in = new FileInputStream(args[1])) {
            ks.load(in, pass);
        }
        PrivateKey key = (PrivateKey) ks.getKey(alias, pass);
        X509Certificate cert = (X509Certificate) ks.getCertificate(alias);

        ApkSigner.SignerConfig signer = new ApkSigner.SignerConfig.Builder(
                "debug", key, Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(signer))
                .setInputApk(new File(args[4]))
                .setOutputApk(new File(args[5]))
                .setMinSdkVersion(26)
                // minSdk 26: v2 imza Android 7+ için yeterli. Eski apksig'in v1 yolu JDK 21 ile çalışmıyor.
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .build()
                .sign();
        System.out.println("imzalandı: " + args[5]);
    }
}
