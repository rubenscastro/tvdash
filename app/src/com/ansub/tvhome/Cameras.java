package com.ansub.tvhome;

// The cameras, in grid order: {stream name on the home server, label}.
// Video comes from the server's RTSP port (go2rtc), so the names are its stream names.
final class Cameras {
    static final String HOST = "10.0.20.10";
    static final int PORT = 8554;
    static final String[][] LIST = {
        {"cam_b23ede0c", "Calçada"},
        {"rua", "Rua"},
        {"garagem", "Garagem"},
        {"corredor", "Corredor"},
        {"fundo", "Fundo"},
    };
}
