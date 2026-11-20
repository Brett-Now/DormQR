import app.dormqr.qr.QrHttp;

/**
 * 桌面端验证：用 App 里同一份 QrHttp 打真实接口，
 * 验证"取码 + 解析"。
 *
 * 用法：
 *   java -cp tools\outhttp;orgjson.jar HttpCheck <endpoint> <payload>
 */
public final class HttpCheck {

    public static void main(String[] args) {
        String endpoint = args.length > 0 ? args[0]
                : "https://gy.bostex.net:5500/wxProgram/system/companyEncryption";
        String payload = args.length > 1 ? args[1] : "";

        int fail = 0;

        // 1) 正常路径：真实请求
        System.out.println("-- 1. 真实请求 --");
        try {
            long t0 = System.currentTimeMillis();
            QrHttp.Result r1 = QrHttp.fetch(endpoint, payload);
            long cost = System.currentTimeMillis() - t0;
            System.out.println("[PASS] 取到 " + r1.content.length() + " 字符，耗时 " + cost + " ms");
            System.out.println("       前 11 位: " + r1.content.substring(0, Math.min(11, r1.content.length())));
            boolean okPrefix = r1.content.startsWith("VMS://v001.");
            System.out.println((okPrefix ? "[PASS]" : "[FAIL]") + " 协议前缀 VMS://v001.");
            if (!okPrefix) fail++;

            // 再取一次，确认动态
            QrHttp.Result r2 = QrHttp.fetch(endpoint, payload);
            boolean differs = !r1.content.equals(r2.content);
            System.out.println((differs ? "[PASS]" : "[FAIL]") + " 两次取码内容不同（动态生成）");
            if (!differs) fail++;

        } catch (Exception e) {
            System.out.println("[FAIL] 真实请求失败: " + e.getMessage());
            fail++;
        }

        // 2) 形态判定
        System.out.println("-- 2. 响应形状判定 --");
        String personnel = "{\"code\":\"200\",\"msg\":\"success\",\"body\":{\"data\":[{\"staffNo\":\"000000000000\"}]}}";
        try {
            QrHttp.extractStaffQrCode(personnel);
            System.out.println("[FAIL] 人员资料被误当成二维码");
            fail++;
        } catch (QrHttp.ApiException e) {
            System.out.println("[PASS] 正确拦截: " + e.getMessage());
        }

        String business = "{\"code\":\"500\",\"msg\":\"服务器错误\"}";
        try {
            QrHttp.extractStaffQrCode(business);
            System.out.println("[FAIL] 业务失败未被识别");
            fail++;
        } catch (QrHttp.ApiException e) {
            System.out.println("[PASS] 正确拦截: " + e.getMessage());
        }

        String qrJson = "{\"code\":\"200\",\"msg\":\"message\",\"body\":{\"staff\":[{\"staffQRCode\":\"VMS://v001.test\"}]}}";
        try {
            String qr = QrHttp.extractStaffQrCode(qrJson);
            boolean ok = "VMS://v001.test".equals(qr);
            System.out.println((ok ? "[PASS]" : "[FAIL]") + " 单测样例解析: " + qr);
            if (!ok) fail++;
        } catch (QrHttp.ApiException e) {
            System.out.println("[FAIL] 样例解析失败: " + e.getMessage());
            fail++;
        }

        System.out.println();
        System.out.println(fail == 0 ? "全部通过" : ("失败 " + fail + " 项"));
        System.exit(fail == 0 ? 0 : 1);
    }
}
