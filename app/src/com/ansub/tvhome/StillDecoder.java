package com.ansub.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaFormat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

// Decodes one keyframe into a Bitmap with a short-lived decoder. Used for the camera grid's
// previews: only one decoder exists at a time, since the TV can't run five at once.
final class StillDecoder {
    static synchronized Bitmap decode(String codec, byte[] annexB, int sample) {
        MediaCodec dec = null;
        try {
            String mime = codec.equals("H264") ? "video/avc" : "video/hevc";
            dec = MediaCodec.createDecoderByType(mime);
            dec.configure(MediaFormat.createVideoFormat(mime, 1920, 1080), null, null, 0);
            dec.start();
            int in = dec.dequeueInputBuffer(1000000);
            if (in < 0) return null;
            ByteBuffer buf = dec.getInputBuffer(in);
            buf.clear();
            buf.put(annexB);
            dec.queueInputBuffer(in, 0, annexB.length, 0, 0);
            int eos = dec.dequeueInputBuffer(1000000);
            if (eos >= 0) dec.queueInputBuffer(eos, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long end = System.currentTimeMillis() + 4000;
            while (System.currentTimeMillis() < end) {
                int o = dec.dequeueOutputBuffer(info, 100000);
                if (o >= 0) {
                    Bitmap bmp = null;
                    if (info.size > 0) {
                        Image img = dec.getOutputImage(o);
                        if (img != null) { bmp = toBitmap(img, sample); img.close(); }
                    }
                    dec.releaseOutputBuffer(o, false);
                    if (bmp != null) return bmp;
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
                }
            }
        } catch (Exception ignored) {
        } finally {
            try { if (dec != null) { dec.stop(); dec.release(); } } catch (Exception ignored) {}
        }
        return null;
    }

    private static Bitmap toBitmap(Image img, int sample) {
        int w = img.getWidth(), h = img.getHeight();
        byte[] nv21 = new byte[w * h * 3 / 2];
        Image.Plane[] p = img.getPlanes();
        copyPlane(p[0], w, h, nv21, 0, 1);
        // NV21 wants interleaved V,U
        copyPlane(p[2], w / 2, h / 2, nv21, w * h, 2);
        copyPlane(p[1], w / 2, h / 2, nv21, w * h + 1, 2);
        ByteArrayOutputStream jpg = new ByteArrayOutputStream();
        new YuvImage(nv21, ImageFormat.NV21, w, h, null).compressToJpeg(new Rect(0, 0, w, h), 85, jpg);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        byte[] j = jpg.toByteArray();
        return BitmapFactory.decodeByteArray(j, 0, j.length, o);
    }

    private static void copyPlane(Image.Plane pl, int w, int h, byte[] dst, int off, int step) {
        ByteBuffer b = pl.getBuffer();
        int rs = pl.getRowStride(), ps = pl.getPixelStride();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                dst[off + y * w * step + x * step] = b.get(y * rs + x * ps);
            }
        }
    }
}
