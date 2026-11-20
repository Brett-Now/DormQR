package app.dormqr.qr;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 最近一次取到的二维码，带取码时间。
 */
public final class QrStore {

    private static final String PREFS = "dormqr_store";

    private final SharedPreferences sp;

    public QrStore(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static final class Entry {
        public final String content;
        public final long retrievedAt;
        public final String source;

        Entry(String content, long retrievedAt, String source) {
            this.content = content;
            this.retrievedAt = retrievedAt;
            this.source = source;
        }

        public long ageMillis() {
            return Math.max(0L, System.currentTimeMillis() - retrievedAt);
        }
    }

    public void save(String content, long retrievedAt, String source) {
        sp.edit()
                .putString("content", content)
                .putLong("retrievedAt", retrievedAt)
                .putString("source", source)
                .apply();
    }

    public Entry load() {
        String content = sp.getString("content", null);
        if (content == null || content.isEmpty()) return null;

        return new Entry(content, sp.getLong("retrievedAt", 0L),
                sp.getString("source", ""));
    }

    public void clear() {
        sp.edit().clear().apply();
    }
}
