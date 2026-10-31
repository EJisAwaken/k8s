package org.esoalahy.stock.core.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.qrcode.QRCodeWriter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Fabrique de QR codes et de codes-barres Code 128 en PNG — utilitaire pur,
 * sans état ni dépendance Spring. Sert aux étiquettes produits (cahier §3.1).
 */
public final class QrCodes {

    public static final int TAILLE_DEFAUT = 320;

    private QrCodes() {
    }

    public static byte[] png(String contenu, int taille) {
        exigerContenu(contenu);
        try {
            BitMatrix matrix = new QRCodeWriter().encode(contenu, BarcodeFormat.QR_CODE, taille, taille,
                    Map.of(EncodeHintType.MARGIN, 1));
            return enPng(matrix);
        } catch (WriterException e) {
            throw new IllegalStateException("Impossible de générer ce QR.", e);
        }
    }

    public static byte[] png(String contenu) {
        return png(contenu, TAILLE_DEFAUT);
    }

    /**
     * Code-barres Code 128 — lisible par toute douchette USB/Bluetooth.
     * Le contenu doit être en ASCII imprimable.
     */
    public static byte[] code128(String contenu, int largeur, int hauteur) {
        exigerContenu(contenu);
        try {
            BitMatrix matrix = new Code128Writer().encode(contenu, BarcodeFormat.CODE_128, largeur, hauteur,
                    Map.of(EncodeHintType.MARGIN, 4));
            return enPng(matrix);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Ce code ne peut pas être imprimé en code-barres : « " + contenu + " ».", e);
        }
    }

    private static void exigerContenu(String contenu) {
        if (contenu == null || contenu.isBlank()) {
            throw new IllegalArgumentException("Un code sans contenu n'existe pas.");
        }
    }

    private static byte[] enPng(BitMatrix matrix) {
        BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < matrix.getWidth(); x++) {
            for (int y = 0; y < matrix.getHeight(); y++) {
                image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
