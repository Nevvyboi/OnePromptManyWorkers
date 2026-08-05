package com.bbd.live;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/** The presenter's join QR, generated on the fly for whatever address we're on. */
public final class Qr {
    private Qr() {}

    public static byte[] png(String text, int size) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
            var config = new MatrixToImageConfig(0xFF0B0D12, 0xFFFFFFFF); // ink on white
            var out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out, config);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("QR generation failed", e);
        }
    }
}
