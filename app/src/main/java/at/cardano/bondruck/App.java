package at.cardano.bondruck;

import android.app.Application;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Speichert Abstürze, damit sie beim nächsten Start angezeigt werden können. */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                String s = sw.toString();
                if (s.length() > 3000) s = s.substring(0, 3000);
                getSharedPreferences("crash", MODE_PRIVATE).edit().putString("last", s).commit();
            } catch (Throwable ignored) { }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }
}
