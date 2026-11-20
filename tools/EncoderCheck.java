/*
 * 桌面端验证：用 App 里同一份 io.nayuki.qrcodegen 生成二维码 PNG，
 * 再用 ZXing 解码比对，确认"内容 → 图片"正确。
 * 只在开发机跑。
 *
 * 编译运行：
 *   javac -encoding UTF-8 -d tools\out ^
 *     app\src\main\java\io\nayuki\qrcodegen\*.java tools\EncoderCheck.java
 *   java -cp tools\out EncoderCheck "VMS://v001.abc" artifacts\android_qr_probe.png
 */
import io.nayuki.qrcodegen.QrCode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public final class EncoderCheck {

    public static void main(String[] args) throws Exception {
        // 默认值只是占位样例，不是任何真实凭证；验证时请自行传入内容
        String content = args.length > 0 ? args[0] : "VMS://v001.SAMPLE0000TEST0000000000";
        String outPath = args.length > 1 ? args[1] : "qr_probe.png";
        int scale = args.length > 2 ? Integer.parseInt(args[2]) : 8;
        int quiet = 4;

        QrCode qr = QrCode.encodeText(content, QrCode.Ecc.MEDIUM);
        int modules = qr.size + quiet * 2;
        int px = modules * scale;

        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < px; y++) {
            int my = y / scale - quiet;
            for (int x = 0; x < px; x++) {
                int mx = x / scale - quiet;
                boolean dark = mx >= 0 && my >= 0 && mx < qr.size && my < qr.size
                        && qr.getModule(mx, my);
                img.setRGB(x, y, dark ? 0x000000 : 0xFFFFFF);
            }
        }

        File out = new File(outPath);
        if (out.getParentFile() != null) out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);

        System.out.println("version=" + qr.version
                + " size=" + qr.size + " modules"
                + " ecc=" + qr.errorCorrectionLevel
                + " mask=" + qr.mask);
        System.out.println("content=" + content);
        System.out.println("png=" + out.getAbsolutePath() + " (" + px + "px)");
    }
}
