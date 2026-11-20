package app.dormqr.qr;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 全部可调项集中在这里，界面只负责显示与触发。
 * 默认值来自 res/values/secrets.xml 与 res/values/strings.xml，
 * 用户改过之后以 SharedPreferences 为准。
 */
public final class QrSettings {

    private static final String PREFS = "dormqr_settings";

    // 默认值 key（仅首次读取时作为兜底）
    public static final String DEF_BASE_URL = "https://gy.bostex.net:5500";
    public static final String DEF_QR_PATH = "/wxProgram/system/companyEncryption";
    public static final int DEF_TTL_SECONDS = 600;          // 实测有效期下限 10 分钟
    public static final int DEF_REFRESH_PERCENT = 80;       // 过 80% = 8 分钟换码
    public static final int DEF_CACHE_SECONDS = 300;

    private final SharedPreferences sp;
    private final String defaultPayload;

    public QrSettings(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        defaultPayload = ctx.getString(R.string.default_payload);
    }

    public String baseUrl() {
        return sp.getString("baseUrl", DEF_BASE_URL);
    }

    public String qrPath() {
        return sp.getString("qrPath", DEF_QR_PATH);
    }

    public String payload() {
        String v = sp.getString("payload", null);
        return v == null || v.trim().isEmpty() ? defaultPayload : v.trim();
    }

    public int ttlSeconds() {
        return clamp(sp.getInt("ttlSeconds", DEF_TTL_SECONDS), 60, 3600);
    }

    public int refreshPercent() {
        return clamp(sp.getInt("refreshPercent", DEF_REFRESH_PERCENT), 10, 100);
    }

    public int cacheSeconds() {
        return clamp(sp.getInt("cacheSeconds", DEF_CACHE_SECONDS), 0, 3600);
    }

    /** 后台保活开关：关掉后不再有常驻通知。 */
    public boolean backgroundEnabled() {
        return sp.getBoolean("backgroundEnabled", true);
    }

    /**
     * 二维码页是否抵抗"翻腕熄屏"。
     * ColorOS 手表用腕部传感器判定的熄屏不受 KEEP_SCREEN_ON 约束，
     * 采用收到熄屏广播后立刻把页面顶回来（需要"后台弹出界面/悬浮窗"权限）。
     */
    public boolean keepAwakeOnWrist() {
        return sp.getBoolean("keepAwakeOnWrist", true);
    }

    public void setKeepAwakeOnWrist(boolean on) {
        sp.edit().putBoolean("keepAwakeOnWrist", on).apply();
    }

    /** 用本地模拟数据，便于离线调试界面。 */
    public boolean demoMode() {
        return sp.getBoolean("demoMode", false);
    }

    public long refreshAtMillis() {
        return ttlSeconds() * 1000L * refreshPercent() / 100L;
    }

    public void save(String baseUrl, String qrPath, String payload,
                     int ttlSeconds, int refreshPercent, int cacheSeconds,
                     boolean background, boolean demo) {
        sp.edit()
                .putString("baseUrl", baseUrl.trim())
                .putString("qrPath", qrPath.trim())
                .putString("payload", payload.trim())
                .putInt("ttlSeconds", clamp(ttlSeconds, 60, 3600))
                .putInt("refreshPercent", clamp(refreshPercent, 10, 100))
                .putInt("cacheSeconds", clamp(cacheSeconds, 0, 3600))
                .putBoolean("backgroundEnabled", background)
                .putBoolean("demoMode", demo)
                .apply();
    }

    /** 恢复默认：清掉所有覆盖项，回到内置默认值。 */
    public void resetAll() {
        sp.edit().clear().apply();
    }

    public void setBackgroundEnabled(boolean enabled) {
        sp.edit().putBoolean("backgroundEnabled", enabled).apply();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}


