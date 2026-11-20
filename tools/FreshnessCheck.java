import app.dormqr.qr.QrFreshness;

public final class FreshnessCheck {
    public static void main(String[] a) {
        long base = 1_800_000_000_000L;
        int ttl = 600, pct = 80;
        long[] cases = {0, 299_000, 300_000, 479_000, 480_000, 599_000, 600_000, 900_000};
        QrFreshness.State[] expect = {
            QrFreshness.State.FRESH, QrFreshness.State.FRESH, QrFreshness.State.AGING,
            QrFreshness.State.AGING, QrFreshness.State.DUE, QrFreshness.State.DUE,
            QrFreshness.State.EXPIRED, QrFreshness.State.EXPIRED
        };
        int fail = 0;
        for (int i = 0; i < cases.length; i++) {
            QrFreshness.Snapshot s = QrFreshness.evaluate(base, base + cases[i], ttl, pct);
            boolean ok = s.state == expect[i];
            if (!ok) fail++;
            System.out.printf("%s %6ds -> %-8s (期望 %-8s) %s%n",
                    ok ? "[PASS]" : "[FAIL]", cases[i] / 1000, s.state, expect[i],
                    QrFreshness.describe(s));
        }
        System.out.println("换码线: " + QrFreshness.evaluate(base, base + 480_000, ttl, pct).refreshAtMillis + " ms");
        System.out.println(fail == 0 ? "全部通过" : ("失败 " + fail + " 项"));
        System.exit(fail == 0 ? 0 : 1);
    }
}
