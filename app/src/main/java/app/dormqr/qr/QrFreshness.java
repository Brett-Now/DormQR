package app.dormqr.qr;

/**
 * 新鲜度判定。
 * 有效期设定下限 10 分钟，过 80%（8 分钟）即视为该换码。
 * 纯计算，方便单测。
 */
public final class QrFreshness {

    public enum State { FRESH, AGING, DUE, EXPIRED }

    public static final class Snapshot {
        public final State state;
        public final long ageMillis;
        public final long remainingMillis;
        public final long refreshAtMillis;

        Snapshot(State state, long ageMillis, long remainingMillis, long refreshAtMillis) {
            this.state = state;
            this.ageMillis = ageMillis;
            this.remainingMillis = remainingMillis;
            this.refreshAtMillis = refreshAtMillis;
        }

        public boolean usable() {
            return state != State.EXPIRED;
        }
    }

    private QrFreshness() {
    }

    public static Snapshot evaluate(long retrievedAt, long now, int ttlSeconds, int refreshPercent) {
        long ttl = Math.max(10, ttlSeconds) * 1000L;
        int percent = Math.max(10, Math.min(100, refreshPercent));

        long age = Math.max(0L, now - retrievedAt);
        long remaining = Math.max(0L, ttl - age);
        long refreshAt = ttl * percent / 100L;

        State state;
        if (age >= ttl) {
            state = State.EXPIRED;
        } else if (age >= refreshAt) {
            state = State.DUE;
        } else if (age * 2L >= ttl) {
            state = State.AGING;
        } else {
            state = State.FRESH;
        }

        return new Snapshot(state, age, remaining, refreshAt);
    }

    public static boolean refreshNeeded(Snapshot s) {
        return s.state == State.DUE || s.state == State.EXPIRED;
    }

    public static String describe(Snapshot s) {
        long totalSeconds = (s.remainingMillis + 999L) / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;

        switch (s.state) {
            case EXPIRED:
                return "已过期 · 正在换码";
            case DUE:
                return "已到刷新线 · 正在换码";
            default:
                return minutes > 0
                        ? "剩余 " + minutes + " 分 " + String.format("%02d", seconds) + " 秒"
                        : "剩余 " + seconds + " 秒";
        }
    }

    /** 圆表用的极简文案。 */
    public static String describeShort(Snapshot s) {
        long totalSeconds = (s.remainingMillis + 999L) / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;

        switch (s.state) {
            case EXPIRED:
                return "已过期";
            case DUE:
                return "换码中";
            default:
                return String.format("剩余 %d:%02d", minutes, seconds);
        }
    }
}
