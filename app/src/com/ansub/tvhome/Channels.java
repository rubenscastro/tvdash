package com.ansub.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

// The antenna channels shown on the TV Aberta screen, in order:
// {number, name, TV provider channel id, logo file in assets/logos ("" for none), optional "ip" for internet channels}.
// The ids come from this TV's channel list; after a channel rescan they must be mapped again.
final class Channels {
    static final String[][] LIST = {
        {"26.1", "Globo", "246", "globo"},
        {"30.1", "Band", "3303", "band"},
        {"35.1", "SBT", "77", "sbt"},
        {"45.1", "Record", "82", "record"},
        {"2.1", "Record News", "3306", "record_news"},
        {"31.3", "TV Câmara Sorocaba", "69", "camara_sorocaba"},
        {"31.1", "TV Câmara", "67", "camara"},
        {"31.4", "TV Senado", "70", "senado"},
        {"31.2", "TV Alesp", "68", "alesp"},
        // internet channels of the TV maker's own app (they use its IP input, not the antenna); ids found by scanning
        {"3387", "TV Cultura", "3214", "cultura", "ip"},
        {"10030", "BandNews FM", "3169", "bandnews_fm", "ip"},
        {"10031", "SBT News", "3171", "sbt_news", "ip"},
    };

    // Everything else the TV can tune (the "Todos" tab): {number, name, TV provider channel id, logo, "ip" or "", category}.
    static final String[][] EXTRA = {
        {"4.1", "Band Mais HD", "48", "band_mais_hd", "", "Abertos"},
        {"8.1", "RFTV HD", "50", "", "", "Abertos"},
        {"15.1", "Rede Brasil HD", "58", "", "", "Abertos"},
        {"23.1", "Band Mais HD", "61", "band_mais_hd", "", "Abertos"},
        {"32.2", "Rede Vida HD", "73", "rede_vida_hd", "", "Religiosos"},
        {"41.1", "TV Cultura HD", "3309", "", "", "Educativos"},
        {"48.1", "Rede TV Sorocaba", "83", "", "", "Abertos"},
        {"57.1", "Gazeta HD", "86", "", "", "Abertos"},
        {"136", "Em Nome do Penhor", "3226", "", "ip", "Filmes e séries"},
        {"137", "Sessão Trash", "3166", "", "ip", "Filmes e séries"},
        {"138", "SFT Combat", "3257", "", "ip", "Esportes"},
        {"139", "Adrenalina Pura TV Halloween", "3217", "", "ip", "Filmes e séries"},
        {"140", "Booh!", "3298", "", "ip", "Filmes e séries"},
        {"141", "Runtime", "3268", "runtime", "ip", "Filmes e séries"},
        {"142", "Highway To Heaven", "3254", "highway_to_heaven", "ip", "Filmes e séries"},
        {"146", "Detetives Médicos", "3221", "detetives_medicos", "ip", "Filmes e séries"},
        {"147", "Z Nation", "3223", "z_nation", "ip", "Filmes e séries"},
        {"150", "Filmelier TV Paixão", "3219", "", "ip", "Filmes e séries"},
        {"151", "Assombrações", "3212", "", "ip", "Filmes e séries"},
        {"152", "Western Bound Português", "3170", "", "ip", "Filmes e séries"},
        {"153", "Netmovies", "3290", "", "ip", "Filmes e séries"},
        {"154", "Sobrevivi", "3185", "", "ip", "Filmes e séries"},
        {"155", "Tastemade Brasil", "3176", "tastemade_brasil", "ip", "Variedades"},
        {"156", "Tastemade Casa", "3180", "", "ip", "Variedades"},
        {"165", "Cine Família", "3299", "", "ip", "Filmes e séries"},
        {"2000", "New Brasil", "3250", "", "ip", "Esportes"},
        {"2010", "Times Brasil | CNBC", "3291", "", "ip", "Notícias"},
        {"2020", "CNN Brasil Money", "3218", "", "ip", "Notícias"},
        {"2030", "BM&C NEWS", "3292", "bm_c_news", "ip", "Notícias"},
        {"2060", "Euronews Português", "3278", "", "ip", "Notícias"},
        {"2070", "TV Aparecida", "3209", "tv_aparecida", "ip", "Religiosos"},
        {"2072", "Rede Vida", "3167", "rede_vida", "ip", "Religiosos"},
        {"2075", "TV Evangelizar Plus", "3208", "tv_evangelizar_plus", "ip", "Religiosos"},
        {"2105", "Runtime Crime", "3273", "", "ip", "Filmes e séries"},
        {"2106", "Film Plus TV", "3155", "", "ip", "Filmes e séries"},
        {"2107", "Ação Total TV", "3200", "", "ip", "Filmes e séries"},
        {"2108", "Film Plus Action TV", "3153", "", "ip", "Filmes e séries"},
        {"2109", "Medo TV", "3201", "", "ip", "Filmes e séries"},
        {"2110", "Runtime Familia", "3247", "", "ip", "Filmes e séries"},
        {"2112", "Film Plus Family TV", "3152", "", "ip", "Filmes e séries"},
        {"2115", "Runtime Comédia", "3271", "runtime_comedia", "ip", "Filmes e séries"},
        {"2120", "Spark TV Luz E Amor", "3248", "", "ip", "Religiosos"},
        {"2125", "Romance Channel", "3241", "", "ip", "Filmes e séries"},
        {"2130", "Runtime Romance", "3274", "runtime_romance", "ip", "Filmes e séries"},
        {"2135", "Moviesphere", "3239", "moviesphere", "ip", "Filmes e séries"},
        {"2137", "Film Plus Emotion TV", "3151", "", "ip", "Filmes e séries"},
        {"2138", "Film Plus Shorts TV", "3154", "", "ip", "Filmes e séries"},
        {"2140", "Filmelier TV", "3230", "", "ip", "Filmes e séries"},
        {"2145", "Filmelier TV Esperança", "3216", "", "ip", "Filmes e séries"},
        {"2150", "Filmelier TV Vida Real", "3215", "", "ip", "Filmes e séries"},
        {"2155", "CINDIE LITE", "3240", "", "ip", "Filmes e séries"},
        {"2165", "Andromeda", "3222", "andromeda", "ip", "Filmes e séries"},
        {"2170", "wedotv amor pele selvagem", "3179", "", "ip", "Filmes e séries"},
        {"2175", "FilmRise Séries Gratuitas", "3255", "", "ip", "Filmes e séries"},
        {"2180", "Novelíssima", "3269", "", "ip", "Filmes e séries"},
        {"2185", "Clube Do Terror", "3162", "", "ip", "Filmes e séries"},
        {"3020", "Turma da Mônica", "3231", "turma_da_monica", "ip", "Infantil"},
        {"3022", "DPA Fast", "3204", "", "ip", "Infantil"},
        {"3023", "O Reino Infantil", "3197", "o_reino_infantil", "ip", "Infantil"},
        {"3025", "3 Palavrinhas", "3228", "", "ip", "Infantil"},
        {"3026", "Toon Goggles em português", "3287", "", "ip", "Infantil"},
        {"3027", "Os Smurfs", "3160", "", "ip", "Infantil"},
        {"3028", "Sonic, O Ouriço", "3164", "", "ip", "Infantil"},
        {"3029", "Inspetor Bugiganga", "3163", "", "ip", "Infantil"},
        {"3030", "Toon Mania", "3242", "", "ip", "Infantil"},
        {"3035", "Moranguinho", "3236", "", "ip", "Infantil"},
        {"3039", "Manual do Mundo", "3149", "", "ip", "Variedades"},
        {"3040", "DumDum", "3252", "", "ip", "Infantil"},
        {"3050", "Teletubbies", "3235", "teletubbies", "ip", "Infantil"},
        {"3055", "TG Junior", "3288", "tg_junior", "ip", "Infantil"},
        {"3070", "ducktv", "3266", "", "ip", "Infantil"},
        {"3075", "SuperToons", "3243", "", "ip", "Infantil"},
        {"3375", "Canal Rural", "3187", "canal_rural", "ip", "Notícias"},
        {"3378", "TV Notícias Agrícolas", "3224", "", "ip", "Notícias"},
        {"3385", "Bloomberg TV+", "3285", "bloomberg_tv", "ip", "Notícias"},
        {"3388", "BRIN TV", "3186", "", "ip", "Variedades"},
        {"3390", "Drone TV", "3284", "", "ip", "Variedades"},
        {"3395", "Nature Moments", "3253", "", "ip", "Variedades"},
        {"3400", "TRAVEL PLUS", "3198", "", "ip", "Variedades"},
        {"3410", "Travelxp", "3244", "travelxp", "ip", "Variedades"},
        {"3510", "GPTV", "3184", "", "ip", "Esportes"},
        {"3560", "RACER Brasil", "3256", "racer_brasil", "ip", "Esportes"},
        {"3710", "GLORY Kickboxing", "3276", "glory_kickboxing", "ip", "Esportes"},
        {"3770", "UBISOFT TV Brasil", "3267", "", "ip", "Variedades"},
        {"3823", "FIFA+", "3238", "", "ip", "Esportes"},
        {"3824", "Man City 24/7", "3157", "", "ip", "Esportes"},
        {"3825", "CANAL GOAT", "3168", "", "ip", "Esportes"},
        {"3827", "Red Bull TV", "3196", "red_bull_tv", "ip", "Esportes"},
        {"3830", "Canal UOL", "3233", "", "ip", "Variedades"},
        {"4020", "Tennis+", "3175", "", "ip", "Esportes"},
        {"4030", "Acelerados", "3147", "", "ip", "Esportes"},
        {"4035", "Desimpedidos", "3150", "", "ip", "Esportes"},
        {"4040", "GFN Football TV", "3300", "", "ip", "Esportes"},
        {"4460", "FUEL TV", "3293", "fuel_tv", "ip", "Esportes"},
        {"4470", "DiaTV", "3259", "diatv", "ip", "Variedades"},
        {"4525", "Olá, Mundo", "3172", "", "ip", "Variedades"},
        {"4822", "Adoro Cinema", "3173", "", "ip", "Filmes e séries"},
        {"4880", "Veja+", "3192", "veja", "ip", "Notícias"},
        {"4885", "WhE Play+", "3302", "", "ip", "Variedades"},
        {"4886", "Nerd Show", "3191", "", "ip", "Variedades"},
        {"4887", "TV Flow", "3211", "", "ip", "Variedades"},
        {"4888", "Podpah", "3295", "", "ip", "Variedades"},
        {"4889", "Porta TV", "3202", "", "ip", "Variedades"},
        {"4890", "SHIFT", "3249", "", "ip", "Variedades"},
        {"4900", "Sabor & Arte", "3245", "", "ip", "Variedades"},
        {"4902", "Receitas Fast", "3203", "receitas_fast", "ip", "Variedades"},
        {"4905", "Caras+", "3165", "", "ip", "Variedades"},
        {"4980", "Canal do Artesanato", "3145", "", "ip", "Variedades"},
        {"4990", "Homeful", "3296", "homeful", "ip", "Variedades"},
        {"5300", "Woohoo Surf", "3251", "", "ip", "Esportes"},
        {"5390", "FitDance", "3220", "", "ip", "Variedades"},
        {"5480", "Fatos Desconhecidos", "3148", "", "ip", "Variedades"},
        {"5490", "Tastemade Viagem", "3177", "", "ip", "Variedades"},
        {"6110", "Stingray Easy Listening", "3279", "stingray_easy_listening", "ip", "Música"},
        {"6120", "Stingray Naturescape", "3282", "stingray_naturescape", "ip", "Variedades"},
        {"6260", "Qello Concerts by Stingray", "3280", "qello_concerts_by_stingray", "ip", "Música"},
        {"6510", "Stingray Classica", "3281", "stingray_classica", "ip", "Música"},
        {"7502", "Wedo Big Stories", "3294", "wedo_big_stories", "ip", "Variedades"},
        {"8555", "Trace Latina", "3286", "trace_latina", "ip", "Música"},
        {"9017", "Play TV", "3229", "play_tv", "ip", "Notícias"},
        {"9018", "SATO+", "3272", "", "ip", "Anime"},
        {"9430", "Brazilian Pop", "3181", "", "ip", "Música"},
        {"9432", "TikTok Radio Brazil", "3182", "", "ip", "Música"},
        {"9434", "Total Hits Brazil", "3190", "", "ip", "Música"},
        {"9440", "Stingray Greatest Hits", "3283", "", "ip", "Música"},
        {"9445", "That's ROCK", "3264", "", "ip", "Música"},
        {"9450", "That's 80s", "3263", "", "ip", "Música"},
        {"9535", "World Poker Tour Portuguese", "3174", "", "ip", "Esportes"},
        {"10005", "SportyNet", "3156", "", "ip", "Esportes"},
        {"10014", "Nsports", "3146", "", "ip", "Esportes"},
        {"10016", "Strongman Champions League", "3193", "strongman_champions_league", "ip", "Esportes"},
        {"10018", "Captain Tsubasa", "3206", "", "ip", "Anime"},
        {"10019", "Jojo’s Bizarre Adventure", "3188", "", "ip", "Anime"},
        {"10020", "Naruto", "3210", "naruto", "ip", "Anime"},
        {"10021", "Inuyasha", "3195", "", "ip", "Anime"},
        {"10023", "Hunter x Hunter", "3194", "", "ip", "Anime"},
        {"10024", "Boruto: Naruto Next Generation", "3189", "", "ip", "Anime"},
        {"10025", "PFL MMA", "3277", "pfl_mma", "ip", "Esportes"},
        {"10027", "CinEspanto", "3246", "", "ip", "Filmes e séries"},
        {"10029", "Metrópoles", "3161", "", "ip", "Notícias"},
    };

    // Categories of the channels of LIST (the main tab).
    static String categoryOfMain(String id) {
        switch (id) {
            case "246": return "Abertos";
            case "3303": return "Abertos";
            case "77": return "Abertos";
            case "82": return "Abertos";
            case "3306": return "Notícias";
            case "69": return "Legislativos";
            case "67": return "Legislativos";
            case "70": return "Legislativos";
            case "68": return "Legislativos";
            case "3214": return "Educativos";
            case "3169": return "Notícias";
            case "3171": return "Notícias";
            default: return "Variedades";
        }
    }

    // The TV input that plays those channels.
    static final String IP_INPUT = "com.tcl.channelplus/com.tcl.iptv.service.InputService";

    // The "TV aberta" row on the home screen: {name, channel id, logo, gradient start, end, CSS angle, start %, end %} (Figma tiles 12:1855-12:1880).
    static final String[][] HOME = {
        {"Globo", "246", "globo", "#FFFFFF", "#AAAABE", "174.53", "4.37", "95.63"},
        {"SBT", "77", "sbt", "#FE4443", "#FEA72C", "354.53", "4.16", "95.42"},
        {"Band", "3303", "band", "#1D1D1D", "#1D1D1D", "174.53", "6.14", "97.39"},
        {"Record", "82", "record", "#A1B1D7", "#EFF1F9", "356.71", "3.5815", "94.874"},
    };

    // The channel row last tuned on the TV Aberta screen (the first one if none yet).
    static String[] lastWatched(Context c) {
        long id = c.getSharedPreferences("tvhome", 0).getLong("last_channel", -1);
        for (String[] row : LIST) if (Long.parseLong(row[2]) == id) return row;
        return LIST[0];
    }

    private static final Map<String, Bitmap> CACHE = new HashMap<>();

    // Logos for the channel banner and list: the ones from the Figma design (fg_*.png) when there is one,
    // decoded down to about the size they are shown at.
    static Bitmap listLogo(Context c, String name, int sizePx) {
        if (name == null || name.isEmpty()) return null;
        String key = "L" + sizePx + name;
        Bitmap b = CACHE.get(key);
        if (b != null) return b;
        String[] tries = {"logos/fg_" + name + ".png", "logos/" + name + ".png"};
        for (String path : tries) {
            try (InputStream in = c.getAssets().open(path)) {
                BitmapFactory.Options probe = new BitmapFactory.Options();
                probe.inJustDecodeBounds = true;
                byte[] data = readAll(in);
                BitmapFactory.decodeByteArray(data, 0, data.length, probe);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = Math.max(1, Math.min(probe.outWidth, probe.outHeight) / (sizePx * 2));
                b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                if (b != null) { CACHE.put(key, b); return b; }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    static Bitmap logo(Context c, String name) {
        if (name == null || name.isEmpty()) return null;
        Bitmap b = CACHE.get(name);
        if (b != null) return b;
        try (InputStream in = c.getAssets().open("logos/" + name + ".png")) {
            b = BitmapFactory.decodeStream(in);
            CACHE.put(name, b);
        } catch (Exception ignored) {}
        return b;
    }
}
