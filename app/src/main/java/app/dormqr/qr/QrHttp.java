package app.dormqr.qr;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 纯 Java 的网络与解析层：不引用任何 Android 类。
 * Android 侧由 QrApi 做配置注入。
 */
public final class QrHttp {

    private static final String MINI_PROGRAM_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/150.0.0.0 Safari/537.36 MicroMessenger/7.0.20.1781(0x6700143B) "
                    + "NetType/WIFI MiniProgramEnv/Windows WindowsWechat/WMPF "
                    + "WindowsWechat(0x63090a13) UnifiedPCWindowsWechat(0xf2541f38) XWEB/26509";

    public static final class Result {
        public final String content;
        public final long retrievedAt;

        public Result(String content, long retrievedAt) {
            this.content = content;
            this.retrievedAt = retrievedAt;
        }
    }

    public static final class ApiException extends Exception {
        public ApiException(String message) {
            super(message);
        }
    }

    private QrHttp() {
    }

    /**
     * @param endpoint 完整 URL
     * @param payload  HAR 里抓到的加密串（data 字段内容）
     */
    public static Result fetch(String endpoint, String payload) throws ApiException {
        if (payload == null || payload.isEmpty()) {
            throw new ApiException("密文未配置");
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("language", "zh-CN");
            conn.setRequestProperty("xweb_xhr", "1");
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("User-Agent", MINI_PROGRAM_UA);

            String body = "{\"data\":\"" + payload + "\"}";
            OutputStream out = conn.getOutputStream();
            try {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            } finally {
                out.close();
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                throw new ApiException("HTTP " + code);
            }

            InputStream in = conn.getInputStream();
            String text;
            try {
                text = readAll(in);
            } finally {
                in.close();
            }

            return new Result(extractStaffQrCode(text), System.currentTimeMillis());

        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException("网络失败：" + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 从响应里取 body.staff[0].staffQRCode；形状不符则抛带说明的异常。 */
    public static String extractStaffQrCode(String json) throws ApiException {
        JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (Exception e) {
            throw new ApiException("响应不是合法 JSON");
        }

        String code = root.optString("code", "");
        if (!"200".equals(code) && !"0".equals(code)) {
            throw new ApiException("业务失败 code=" + code + " msg=" + root.optString("msg", ""));
        }

        JSONObject body = root.optJSONObject("body");
        if (body == null) {
            throw new ApiException("响应缺少 body");
        }

        JSONArray staff = body.optJSONArray("staff");
        if (staff != null && staff.length() > 0) {
            JSONObject first = staff.optJSONObject(0);
            String qr = first == null ? "" : first.optString("staffQRCode", "");
            if (!qr.isEmpty()) return qr.trim();
            throw new ApiException("body.staff 存在但没有 staffQRCode");
        }

        JSONArray data = body.optJSONArray("data");
        if (data != null && data.length() > 0) {
            JSONObject first = data.optJSONObject(0);
            String staffNo = first == null ? "" : first.optString("staffNo", "");
            throw new ApiException("这是人员资料响应（" + (staffNo.isEmpty() ? "无编号" : staffNo)
                    + "），不是二维码。人员查询与二维码查询的密文不能混用。");
        }

        throw new ApiException("无法识别的响应结构");
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }
}
