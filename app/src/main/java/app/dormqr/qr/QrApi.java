package app.dormqr.qr;

/**
 * Android 侧的取码入口：把 QrSettings 里的配置交给纯 Java 的 QrHttp，
 * 顺便处理"模拟模式"。
 */
public final class QrApi {

    public static final class Result {
        public final String content;
        public final long retrievedAt;

        Result(String content, long retrievedAt) {
            this.content = content;
            this.retrievedAt = retrievedAt;
        }
    }

    public static final class ApiException extends Exception {
        public ApiException(String message) {
            super(message);
        }
    }

    private QrApi() {
    }

    public static Result fetch(QrSettings settings) throws ApiException {
        if (settings.demoMode()) {
            return demoResult();
        }

        String endpoint = settings.baseUrl().replaceAll("/+$", "") + "/"
                + settings.qrPath().replaceAll("^/+", "");

        try {
            QrHttp.Result r = QrHttp.fetch(endpoint, settings.payload());
            return new Result(r.content, r.retrievedAt);
        } catch (QrHttp.ApiException e) {
            throw new ApiException(e.getMessage());
        }
    }

    /** 模拟数据：不联网，用于调界面。 */
    private static Result demoResult() {
        String tail = Long.toString(System.currentTimeMillis() % 100000L, 36).toUpperCase();
        String content = "VMS://v001.DEMO" + ("00000" + tail).substring(tail.length());
        return new Result(content, System.currentTimeMillis());
    }
}
