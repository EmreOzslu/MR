package androidx.test.platform.app;

import android.app.Instrumentation;
import android.os.Bundle;

/**
 * SADECE SANDBOX TEST ÇALIŞTIRICISI İÇİN. Robolectric, androidx.test:monitor'deki bu sınıfı çağırır;
 * o kütüphane Google Maven'da olduğu ve bu ortamdan erişilemediği için yerine bu yer tutucu konur.
 * Gradle/Android Studio yolunda gerçek androidx.test kütüphanesi kullanılır, bu dosya kullanılmaz.
 */
public final class InstrumentationRegistry {
    private static Instrumentation instrumentation;
    private static Bundle arguments;

    private InstrumentationRegistry() {
    }

    public static void registerInstance(Instrumentation instrumentation, Bundle arguments) {
        InstrumentationRegistry.instrumentation = instrumentation;
        InstrumentationRegistry.arguments = arguments;
    }

    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }

    public static Bundle getArguments() {
        return arguments;
    }
}
