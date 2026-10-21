package com.ansub.tvhome;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Base64;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

// A small RTSP player for the camera streams: RTSP over TCP, H.264 or H.265 video only, decoded by
// the TV's hardware decoder straight onto a Surface. The system MediaPlayer can't open these.
final class RtspPlayer {
    interface Listener {
        void onState(String state);
        void onSize(int w, int h);
        // Grab mode: the first keyframe (Annex-B, parameter sets included), then the stream is closed.
        default void onGrab(String codec, byte[] keyframe) {}
    }

    private final String host;
    private final int port;
    private final String path;
    private final Surface surface;
    private final Listener listener;
    private volatile boolean stop;
    // Preview mode: only keyframes reach the decoder, so many cameras can share the TV's hardware decoder.
    private volatile boolean grabOnly;
    private volatile boolean keyOnly;
    private volatile boolean needKey;
    private Thread thread;
    private Socket socket;

    RtspPlayer(String host, int port, String path, Surface surface, Listener l) {
        this.host = host; this.port = port; this.path = path; this.surface = surface; this.listener = l;
    }

    void start() {
        thread = new Thread(this::run, "rtsp-" + path);
        thread.start();
    }

    boolean alive() { return !stop && thread != null && thread.isAlive(); }

    void setGrabOnly(boolean v) { grabOnly = v; }

    void setKeyOnly(boolean v) { keyOnly = v; if (v) needKey = true; }

    void stop() {
        stop = true;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        if (thread != null) try { thread.join(1500); } catch (InterruptedException ignored) {}
    }

    // ---- RTSP ----
    private int cseq = 1;
    private String session;
    private DataInputStream in;
    private OutputStream out;
    private String base;

    private String request(String method, String url, String extra) throws IOException {
        StringBuilder sb = new StringBuilder(method + " " + url + " RTSP/1.0\r\nCSeq: " + (cseq++) + "\r\nUser-Agent: tvhome\r\n");
        if (session != null) sb.append("Session: ").append(session).append("\r\n");
        sb.append(extra).append("\r\n");
        out.write(sb.toString().getBytes("UTF-8"));
        out.flush();
        // read headers (skip interleaved data frames that might arrive early)
        StringBuilder head = new StringBuilder();
        int c, last4 = 0;
        while (true) {
            c = in.read();
            if (c < 0) throw new IOException("closed");
            head.append((char) c);
            last4 = (last4 << 8) | c;
            if (last4 == 0x0D0A0D0A) break;
        }
        String h = head.toString();
        int len = 0;
        for (String line : h.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) len = Integer.parseInt(line.substring(15).trim());
            if (line.toLowerCase().startsWith("session:")) session = line.substring(8).trim().split(";")[0];
        }
        byte[] body = new byte[len];
        in.readFully(body);
        if (!h.startsWith("RTSP/1.0 200")) throw new IOException(h.split("\r\n")[0]);
        return h + "\r\n" + new String(body, "UTF-8");
    }

    private String codec; // "H264" or "H265"
    private final List<byte[]> params = new ArrayList<>(); // VPS/SPS/PPS from the SDP, Annex-B ready

    private void run() {
        try {
            listener.onState("conectando");
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 4000);
            socket.setTcpNoDelay(true);
            in = new DataInputStream(new java.io.BufferedInputStream(socket.getInputStream(), 1 << 16));
            out = socket.getOutputStream();
            base = "rtsp://" + host + ":" + port + "/" + path;
            request("OPTIONS", base, "");
            String d = request("DESCRIBE", base, "Accept: application/sdp\r\n");
            String control = parseSdp(d);
            String setupUrl = control == null ? base + "/trackID=0" : control.startsWith("rtsp://") ? control : base + "/" + control;
            request("SETUP", setupUrl, "Transport: RTP/AVP/TCP;unicast;interleaved=0-1\r\n");
            request("PLAY", base, "Range: npt=0.000-\r\n");
            listener.onState("carregando");
            readLoop();
        } catch (Exception e) {
            if (!stop) listener.onState("erro: " + e.getMessage());
        } finally {
            releaseCodec();
            try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        }
    }

    // Finds the first video media section: codec, control URL and the parameter sets.
    private String parseSdp(String sdp) {
        String control = null;
        boolean video = false;
        for (String line : sdp.split("\r\n|\n")) {
            if (line.startsWith("m=")) { if (video) break; video = line.startsWith("m=video"); continue; }
            if (!video) continue;
            if (line.startsWith("a=rtpmap:")) {
                if (line.contains("H264")) codec = "H264";
                else if (line.contains("H265") || line.contains("HEVC")) codec = "H265";
            } else if (line.startsWith("a=control:")) {
                control = line.substring(10).trim();
            } else if (line.startsWith("a=fmtp:")) {
                for (String kv : line.substring(line.indexOf(' ') + 1).split(";")) {
                    kv = kv.trim();
                    int eq = kv.indexOf('=');
                    if (eq < 0) continue;
                    String k = kv.substring(0, eq), v = kv.substring(eq + 1);
                    if (k.equals("sprop-parameter-sets")) {
                        for (String p : v.split(",")) addParam(p);
                    } else if (k.equals("sprop-vps") || k.equals("sprop-sps") || k.equals("sprop-pps")) {
                        addParam(v);
                    }
                }
            }
        }
        if (codec == null) throw new IllegalStateException("sem vídeo H264/H265");
        return control;
    }

    private void addParam(String b64) {
        try { params.add(Base64.decode(b64, Base64.DEFAULT)); } catch (Exception ignored) {}
    }

    // ---- RTP ----
    private void readLoop() throws IOException {
        long lastKeepAlive = System.currentTimeMillis();
        while (!stop) {
            int b = in.read();
            if (b < 0) throw new IOException("conexão encerrada");
            if (b != '$') continue; // stray RTSP text: ignore
            int ch = in.read();
            int len = (in.read() << 8) | in.read();
            byte[] pkt = new byte[len];
            in.readFully(pkt);
            if (ch == 0) onRtp(pkt, len);
            if (System.currentTimeMillis() - lastKeepAlive > 25000) {
                lastKeepAlive = System.currentTimeMillis();
                out.write(("OPTIONS " + base + " RTSP/1.0\r\nCSeq: " + (cseq++) + "\r\nSession: " + session + "\r\n\r\n").getBytes("UTF-8"));
                out.flush();
            }
        }
    }

    private final ByteArrayOutputStream au = new ByteArrayOutputStream();
    private boolean auKey;
    private ByteArrayOutputStream fu;
    private static final byte[] SC = {0, 0, 0, 1};

    private void onRtp(byte[] p, int len) {
        if (len < 13) return;
        boolean marker = (p[1] & 0x80) != 0;
        long ts = ((p[4] & 0xFFL) << 24) | ((p[5] & 0xFF) << 16) | ((p[6] & 0xFF) << 8) | (p[7] & 0xFF);
        int off = 12 + 4 * (p[0] & 0x0F);
        if ((p[0] & 0x10) != 0) off += 4 + 4 * (((p[off + 2] & 0xFF) << 8) | (p[off + 3] & 0xFF));
        if (off >= len) return;
        if (codec.equals("H264")) depack264(p, off, len);
        else depack265(p, off, len);
        if (marker && au.size() > 0) {
            byte[] frame = au.toByteArray();
            au.reset();
            boolean key = auKey;
            auKey = false;
            feed(frame, key, ts * 1000000L / 90000L);
        }
    }

    private void addNal(byte[] src, int off, int len, boolean key) {
        au.write(SC, 0, 4);
        au.write(src, off, len);
        if (key) auKey = true;
    }

    private void depack264(byte[] p, int off, int len) {
        int t = p[off] & 0x1F;
        if (t >= 1 && t <= 23) {
            addNal(p, off, len - off, t == 5 || t == 7);
        } else if (t == 24) { // STAP-A
            int i = off + 1;
            while (i + 2 < len) {
                int sz = ((p[i] & 0xFF) << 8) | (p[i + 1] & 0xFF);
                i += 2;
                if (sz <= 0 || i + sz > len) break;
                int nt = p[i] & 0x1F;
                addNal(p, i, sz, nt == 5 || nt == 7);
                i += sz;
            }
        } else if (t == 28) { // FU-A
            int fuh = p[off + 1] & 0xFF;
            boolean s = (fuh & 0x80) != 0, e = (fuh & 0x40) != 0;
            int nt = fuh & 0x1F;
            if (s) { fu = new ByteArrayOutputStream(); fu.write((p[off] & 0xE0) | nt); }
            if (fu != null) {
                fu.write(p, off + 2, len - off - 2);
                if (e) { byte[] n = fu.toByteArray(); fu = null; addNal(n, 0, n.length, nt == 5); }
            }
        }
    }

    private void depack265(byte[] p, int off, int len) {
        int t = (p[off] >> 1) & 0x3F;
        if (t <= 47 && t != 48 && t != 49) {
            addNal(p, off, len - off, (t >= 16 && t <= 21) || (t >= 32 && t <= 34));
        } else if (t == 48) { // AP
            int i = off + 2;
            while (i + 2 < len) {
                int sz = ((p[i] & 0xFF) << 8) | (p[i + 1] & 0xFF);
                i += 2;
                if (sz <= 0 || i + sz > len) break;
                int nt = (p[i] >> 1) & 0x3F;
                addNal(p, i, sz, (nt >= 16 && nt <= 21) || (nt >= 32 && nt <= 34));
                i += sz;
            }
        } else if (t == 49) { // FU
            int fuh = p[off + 2] & 0xFF;
            boolean s = (fuh & 0x80) != 0, e = (fuh & 0x40) != 0;
            int nt = fuh & 0x3F;
            if (s) {
                fu = new ByteArrayOutputStream();
                fu.write((p[off] & 0x81) | (nt << 1));
                fu.write(p[off + 1]);
            }
            if (fu != null) {
                fu.write(p, off + 3, len - off - 3);
                if (e) { byte[] n = fu.toByteArray(); fu = null; addNal(n, 0, n.length, nt >= 16 && nt <= 21); }
            }
        }
    }

    // ---- decoder ----
    private MediaCodec codecDec;
    private boolean started;

    private byte[] withParams(byte[] frame) {
        ByteArrayOutputStream first = new ByteArrayOutputStream();
        for (byte[] prm : params) { first.write(SC, 0, 4); first.write(prm, 0, prm.length); }
        first.write(frame, 0, frame.length);
        return first.toByteArray();
    }

    private void feed(byte[] frame, boolean key, long usec) {
        if (grabOnly) {
            if (!key) return;
            stop = true;
            listener.onGrab(codec, withParams(frame));
            return;
        }
        if (started) {
            if (keyOnly && !key) { needKey = true; return; }
            if (needKey) { if (!key) return; needKey = false; }
        }
        if (!started) {
            if (!key) return; // wait for a keyframe
            try {
                String mime = codec.equals("H264") ? "video/avc" : "video/hevc";
                MediaFormat fmt = MediaFormat.createVideoFormat(mime, 1920, 1080);
                codecDec = MediaCodec.createDecoderByType(mime);
                codecDec.configure(fmt, surface, null, 0);
                codecDec.start();
                started = true;
                // prepend the parameter sets from the SDP in case the stream doesn't repeat them
                ByteArrayOutputStream first = new ByteArrayOutputStream();
                for (byte[] prm : params) { first.write(SC, 0, 4); first.write(prm, 0, prm.length); }
                first.write(frame, 0, frame.length);
                frame = first.toByteArray();
                startOutput();
                listener.onState("ao vivo");
            } catch (Exception e) {
                listener.onState("erro decoder: " + e);
                stop = true;
                return;
            }
        }
        try {
            int i = codecDec.dequeueInputBuffer(20000);
            if (i < 0) { needKey = true; return; } // decoder busy: drop the frame and wait for a keyframe
            ByteBuffer buf = codecDec.getInputBuffer(i);
            buf.clear();
            if (frame.length > buf.capacity()) { codecDec.queueInputBuffer(i, 0, 0, usec, 0); return; }
            buf.put(frame);
            codecDec.queueInputBuffer(i, 0, frame.length, usec, 0);
        } catch (Exception e) {
            listener.onState("erro decoder: " + e);
            stop = true;
        }
    }

    private void startOutput() {
        final MediaCodec c = codecDec;
        new Thread(() -> {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (!stop) {
                try {
                    int o = c.dequeueOutputBuffer(info, 50000);
                    if (o >= 0) c.releaseOutputBuffer(o, true);
                    else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat f = c.getOutputFormat();
                        listener.onSize(f.getInteger(MediaFormat.KEY_WIDTH), f.getInteger(MediaFormat.KEY_HEIGHT));
                    }
                } catch (Exception e) { break; }
            }
        }, "rtsp-out").start();
    }

    private void releaseCodec() {
        try { if (codecDec != null) { codecDec.stop(); codecDec.release(); } } catch (Exception ignored) {}
        codecDec = null;
    }
}
