package lightvibes.app;

import static lightvibes.app.Ui.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings for lightvibesd: pick a preset, change its colours, save your own.
 *
 * lightvibesd (root) watches files/settings/, one value per file, and applies changes within
 * half a second. Those files are the single source of truth for the current settings; "active"
 * only remembers which preset they came from. Saving over a built-in preset keeps a copy in
 * files/overrides/, which Reset removes; the shipped one in assets is never changed. Deleting a
 * built-in preset leaves a marker in files/hidden/, and restoring them removes the markers.
 */
public class MainActivity extends Activity {
    /* factory 64-colour custom wheels + Hyundai Ioniq 5 0x01xx stock mood colours (64..73), matching lightvibesd.c */
    private static final int[] KIA_R = {16,16,16,33,47,64,82,100,120,136,155,169,183,193,204,213,221,228,235,239,246,250,254,255,255,255,255,255,255,255,255,255,255,255,254,255,246,233,220,202,166,168,149,132,117,104,93,81,71,62,53,44,37,30,25,20,16,16,16,16,16,16,16,16};
    private static final int[] KIA_G = {67,46,32,28,28,28,28,28,28,29,33,35,35,33,31,27,23,19,14,10,6,4,1,50,68,80,105,120,143,166,195,205,218,232,243,255,255,255,255,252,226,243,235,229,223,221,222,225,231,238,245,250,250,250,250,250,244,230,210,187,163,138,111,89};
    private static final int[] KIA_B = {244,242,239,239,239,239,239,239,239,239,239,239,234,224,210,192,174,153,153,116,96,81,69,29,35,39,48,53,62,70,88,118,158,200,230,255,246,221,188,147,91,98,98,98,98,98,105,118,133,152,169,189,207,225,237,249,252,252,252,252,252,252,252,248};

    private static final int[] HYUNDAI_R = {
        176,195,205,232,255,255,255,255,255,255,255,255,254,255,254,254,255,255,255,255,255,255,255,254,255,255,255,255,255,255,255,255,255,254,255,254,247,230,210,175,119,58,10,0,0,0,0,0,0,0,0,0,25,55,81,100,109,122,125,128,130,143,156,167,
        229,244,183,24,3,126,254,233,187,138
    };
    private static final int[] HYUNDAI_G = {
        0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,96,107,73,88,113,125,3,34,48,60,65,137,148,160,172,177,191,207,216,237,255,254,255,254,254,255,255,217,180,161,140,125,115,99,85,62,0,0,0,0,42,52,25,0,0,0,0,0,
        242,233,228,142,199,215,168,62,15,37
    };
    private static final int[] HYUNDAI_B = {
        255,254,254,254,241,195,158,137,117,111,106,101,95,72,58,30,106,116,73,88,113,115,10,0,23,42,49,107,119,111,113,108,123,138,147,165,182,186,188,191,196,198,235,245,255,255,255,255,254,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,
        250,223,252,253,192,21,17,62,236,252
    };

    private static int[] WHEEL_R = KIA_R, WHEEL_G = KIA_G, WHEEL_B = KIA_B;

    private static final String DEFAULT_BANDS = "40-130:#FF0020:1.6 130-450:#FF3C0F:2.0 450-2000:#00FF40:3.2 2000-6000:#00AAFF:9 6000-16000:#9100FF:32";
    private static final String[] BUILT_IN = {
        "lively", "intense", "marigold", "contrast", "pulse3", "glow", "epic",
        "duet", "drop", "synesthesia", "ember", "drift", "nightdrive",
        "speed", "power", "efficiency"
    };
    private static final String FIRST_RUN_PRESET = "b:lively";
    private static final int MAX_BANDS = 8;      // what fits on screen; lightvibesd takes up to 12
    private static final int MIN_SPLIT_HZ = 60;  // each half keeps at least one FFT bin (23.4 Hz each)


    private File settingsDir, presetsDir, overridesDir, hiddenDir, activeFile;
    private Map<String, String> cfg = new LinkedHashMap<String, String>();
    private String active = FIRST_RUN_PRESET;   // "b:<asset name>" or "c:<custom name>"
    private boolean modified;
    private int selectedBand;
    private static final int TAB_COLOR = 0, TAB_BRIGHTNESS = 1, TAB_TUNING = 2;
    private static int tab = TAB_COLOR;   // static: kept when Android restarts the activity for day/night    // the open tab under the mode row, the same for every mode
    private int speedEnd;          // the speed, power or efficiency page's selected end: 0 low, 1 high
    private int duetEnd;           // duet's selected card: 0 melody bed, 1 beat accent
    private TextView speedNow;
    private TextView tuneButton, tuneStatus;
    private boolean tuneActive;
    private long tuneRequestedAt;
    private ReportPage reportPage;  // on screen instead of the editor while set
    private FeedbackPage feedbackPage;  // likewise

    private LinearLayout presetList, editorTop, editor;
    private MeterView meter;
    private volatile Thread meterThread;
    private boolean resumed;
    private TextView saveButton, deleteButton;
    private final Handler handler = new Handler();

    private static final class Band {
        String lo, hi, colour, boost;

        static Band parse(String token) {
            String[] p = token.split(":");
            String[] r = p[0].split("-");
            if (p.length < 2 || r.length != 2) return null;
            Band b = new Band();
            b.lo = r[0];
            b.hi = r[1];
            b.colour = p[1];
            b.boost = p.length > 2 ? p[2] : null;
            return b;
        }

        @Override public String toString() {
            return lo + "-" + hi + ":" + colour + (boost != null ? ":" + boost : "");
        }

        int wheelIndex() {
            return colorIndexOf(colour, 0, 0);
        }

        int rgb() {
            return wheelColour(wheelIndex());
        }

        String title() {
            double mid = Math.sqrt(intOf(lo, 1) * (double) intOf(hi, 1));
            if (mid < 150) return "Bass";
            if (mid < 600) return "Low mids";
            if (mid < 2000) return "Mids";
            if (mid < 6000) return "Highs";
            return "Air";
        }

        String range() {
            return hz(lo) + "–" + hz(hi);
        }

        private static String hz(String v) {
            int f = intOf(v, 0);
            return f >= 1000 ? (f % 1000 == 0 ? (f / 1000) + " kHz" : (f / 100) / 10.0 + " kHz") : f + " Hz";
        }
    }

    /** Black or white, whichever reads better on a colour (by its perceived brightness). */
    private static int readableOn(int c) {
        double y = 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c);
        return y > 150 ? Color.BLACK : Color.WHITE;
    }

    private static double rgbHueDeg(int r, int g, int b) {
        r &= 0xff; g &= 0xff; b &= 0xff;
        int mx = Math.max(r, Math.max(g, b));
        int mn = Math.min(r, Math.min(g, b));
        int d = mx - mn;
        if (d <= 0) return 0.0;
        double h;
        if (mx == r) h = 60.0 * ((g - b) / (double) d);
        else if (mx == g) h = 60.0 * (2.0 + (b - r) / (double) d);
        else h = 60.0 * (4.0 + (r - g) / (double) d);
        if (h < 0.0) h += 360.0;
        return h;
    }

    private static double wheelHue(int idx) {
        if (idx < 0 || idx >= WHEEL_R.length) idx = 0;
        return rgbHueDeg(WHEEL_R[idx], WHEEL_G[idx], WHEEL_B[idx]);
    }

    private static boolean vividWheel(int i) {
        if (i < 0 || i >= WHEEL_R.length) return false;
        int r = WHEEL_R[i], g = WHEEL_G[i], b = WHEEL_B[i];
        int mx = Math.max(r, Math.max(g, b));
        int mn = Math.min(r, Math.min(g, b));
        int minSat = (g >= r && g >= b && g - r >= 55 && g - b >= 75) ? 52 : 78;
        return mx > 0 && (mx - mn) * 100 >= minSat * mx;
    }

    private static int nearestVividWheel(int r, int g, int b) {
        int best = 0, bestD = Integer.MAX_VALUE;
        for (int i = 0; i < WHEEL_R.length; i++) {
            if (!vividWheel(i)) continue;
            int dr = WHEEL_R[i] - r, dg = WHEEL_G[i] - g, db = WHEEL_B[i] - b;
            int d = dr * dr + dg * dg + db * db;
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    private static int vividAtHue(double deg) {
        deg = ((deg % 360.0) + 360.0) % 360.0;
        int best = 0;
        double bestD = 999.0;
        for (int i = 0; i < WHEEL_R.length; i++) {
            if (!vividWheel(i)) continue;
            double h = wheelHue(i);
            double d = Math.abs(h - deg);
            if (d > 180.0) d = 360.0 - d;
            // match lightvibesd.c's hue-sorted vivid_ring tie-breaking
            if (d < bestD - 1e-6 || (Math.abs(d - bestD) <= 1e-6 && h < wheelHue(best))) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private static double hueLerp(double a, double b, double t) {
        double d = b - a;
        if (d > 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return ((a + d * t) % 360.0 + 360.0) % 360.0;
    }

    private static int colorIndexOf(String s, int def, int minSpecial) {
        if (s == null) return def;
        s = s.trim();
        if (s.isEmpty()) return def;
        if (s.startsWith("#") && s.length() == 7) {
            try {
                int hex = Integer.parseInt(s.substring(1), 16);
                return nearestVividWheel((hex >> 16) & 255, (hex >> 8) & 255, hex & 255);
            } catch (NumberFormatException e) {
                return def;
            }
        }
        int v = intOf(s, def);
        if (v < minSpecial) return minSpecial;
        if (v >= WHEEL_R.length) {
            if (v >= 0 && v < HYUNDAI_R.length) return nearestVividWheel(HYUNDAI_R[v], HYUNDAI_G[v], HYUNDAI_B[v]);
            return WHEEL_R.length - 1;
        }
        return v;
    }

    private static int wheelColour(int i) {
        if (i < 0 || i >= WHEEL_R.length) i = 0;
        return Color.rgb(WHEEL_R[i], WHEEL_G[i], WHEEL_B[i]);
    }

    private static int intOf(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s.trim()); } catch (RuntimeException e) { return def; }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------- lifecycle ----------

    @Override protected void onCreate(Bundle saved) {
        Ui.apply(this);     // the head unit's day or night theme
        setTheme(Ui.night ? android.R.style.Theme_Holo_NoActionBar : android.R.style.Theme_Holo_Light_NoActionBar);
        super.onCreate(saved);
        settingsDir = new File(getFilesDir(), "settings");
        presetsDir = new File(getFilesDir(), "presets");
        activeFile = new File(getFilesDir(), "active");
        overridesDir = new File(getFilesDir(), "overrides");
        hiddenDir = new File(getFilesDir(), "hidden");
        presetsDir.mkdirs();
        overridesDir.mkdirs();
        hiddenDir.mkdirs();
        loadCurrent();
        buildScreen();
    }

    @Override protected void onResume() {
        super.onResume();
        // the settings files are the truth: they may have changed while this screen was away (another copy of it,
        // loadpreset.sh, a restart), so show what's there now rather than what was loaded earlier
        if (reportPage == null && feedbackPage == null) {
            loadCurrent();
            refreshPresets();
            refreshEditor();
        }
        resumed = true;
        syncMeter();
        if (reportPage != null) reportPage.resume();
    }

    @Override protected void onPause() {
        resumed = false;
        syncMeter();
        if (reportPage != null) reportPage.stop();
        super.onPause();
    }

    // ---------- settings files ----------

    private void loadCurrent() {
        settingsDir.mkdirs();
        updatePalette();
        cfg.clear();
        String[] names = settingsDir.list();
        if (names != null) {
            Arrays.sort(names);
            for (String n : names) {
                if (n.startsWith(".") || isSwitchFile(n)) continue;
                String v = readFirstLine(new File(settingsDir, n));
                if (v != null) cfg.put(n, v);
            }
        }
        String a = readFirstLine(activeFile);
        if (a != null && a.length() > 2) {
            active = a.endsWith("*") ? a.substring(0, a.length() - 1) : a;
            modified = a.endsWith("*");
        }
        if (cfg.isEmpty()) applyPreset(FIRST_RUN_PRESET);
        else if (!modified && active.startsWith("b:")) {
            // an untouched built-in preset follows the shipped one when an update changes it
            Map<String, String> shipped = readPreset(active);
            if (!shipped.isEmpty() && !shipped.equals(cfg)) applyPreset(active);
        }
    }

    /** enabled, debug and palette belong to the car, not to a preset. */
    private static boolean isSwitchFile(String n) {
        return n.equals("enabled") || n.equals("debug") || n.equals("palette");
    }

    private static String matchMake(String s) {
        if (s == null) return null;
        String l = s.toLowerCase();
        if (l.contains("neev") || l.contains("ceev") || l.contains("ioniq") || l.contains("ecohmc") || l.contains("hyundai")) return "hyundai";
        if (l.contains("cvev") || l.contains("dl3") || l.contains("ev6") || l.contains("ecokia") || l.contains("kia")) return "kia";
        return null;
    }

    private void updatePalette() {
        String p = readFirstLine(new File(settingsDir, "palette"));
        String make = ("hyundai".equals(p) || "kia".equals(p)) ? p : null;
        if (make == null) {
            String[] buildFields = {Build.PRODUCT, Build.DEVICE, Build.MODEL, Build.FINGERPRINT};
            for (String f : buildFields) {
                make = matchMake(f);
                if (make != null) break;
            }
        }
        if (make == null) {
            BufferedReader r = null;
            try {
                r = new BufferedReader(new FileReader("/system/build.prop"));
                String line;
                String[] keys = {"ro.build.product=", "ro.product.device=", "ro.product.name=", "ro.product.model=", "ro.build.fingerprint=", "ro.mobis.onebindir="};
                while ((line = r.readLine()) != null && make == null) {
                    String l = line.toLowerCase();
                    for (String k : keys) {
                        if (l.startsWith(k)) {
                            make = matchMake(l.substring(k.length()));
                            if (make != null) break;
                        }
                    }
                }
            } catch (IOException ignored) {
            } finally {
                if (r != null) try { r.close(); } catch (IOException ignored) { }
            }
        }
        if (make == null) {
            BufferedReader r = null;
            try {
                r = new BufferedReader(new FileReader("/data/hmgapi/settings/hmgapi.json"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
                make = matchMake(sb.toString());
            } catch (IOException ignored) {
            } finally {
                if (r != null) try { r.close(); } catch (IOException ignored) { }
            }
        }
        if ("hyundai".equals(make)) {
            WHEEL_R = HYUNDAI_R; WHEEL_G = HYUNDAI_G; WHEEL_B = HYUNDAI_B;
        } else {
            WHEEL_R = KIA_R; WHEEL_G = KIA_G; WHEEL_B = KIA_B;
        }
    }


    private static String readFirstLine(File f) {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(f));
            String v = r.readLine();
            return v == null ? null : v.trim();
        } catch (IOException e) {
            return null;
        } finally {
            if (r != null) try { r.close(); } catch (IOException ignored) { }
        }
    }

    private static void writeFile(File f, String v) {
        File tmp = new File(f.getParentFile(), "." + f.getName() + ".tmp");
        try {
            FileOutputStream o = new FileOutputStream(tmp);
            try { o.write((v + "\n").getBytes("UTF-8")); } finally { o.close(); }
            if (!tmp.renameTo(f)) {     // a file left by root (loadpreset.sh) can't always be replaced in place
                f.delete();
                tmp.renameTo(f);
            }
        } catch (IOException e) {
            tmp.delete();
        }
    }

    private void writeAllSettings() {
        settingsDir.mkdirs();
        String[] names = settingsDir.list();
        if (names != null) {
            for (String n : names) {
                if (!isSwitchFile(n) && !cfg.containsKey(n)) new File(settingsDir, n).delete();
            }
        }
        for (Map.Entry<String, String> e : cfg.entrySet()) writeFile(new File(settingsDir, e.getKey()), e.getValue());
    }

    private void saveActive() {
        writeFile(activeFile, active + (modified ? "*" : ""));
    }

    /** One setting changed by hand: applied at once, and the preset now counts as edited. */
    private void set(String key, String value) {
        cfg.put(key, value);
        writeFile(new File(settingsDir, key), value);
        modified = true;
        saveActive();
        refreshPresets();
        configureMeter();
    }

    // ---------- presets ----------

    private static String titleOf(String id) {
        String n = id.substring(2);
        if (id.startsWith("c:")) return n;
        if (n.equals("pulse3")) return "Bold Flash";
        if (n.equals("nightdrive")) return "Night Drive";
        if (n.equals("drift")) return "Phrase Drift";
        return n.substring(0, 1).toUpperCase() + n.substring(1);
    }

    private List<String> presetIds() {
        List<String> ids = new ArrayList<String>();
        for (String n : BUILT_IN) if (!new File(hiddenDir, n).exists()) ids.add("b:" + n);
        String[] custom = presetsDir.list();
        if (custom != null) {
            Arrays.sort(custom, String.CASE_INSENSITIVE_ORDER);
            for (String n : custom) if (n.endsWith(".conf")) ids.add("c:" + n.substring(0, n.length() - 5));
        }
        return ids;
    }

    /** A built-in preset as it shipped, ignoring any saved changes. */
    private Map<String, String> readShipped(String name) {
        return readPreset("s:" + name);
    }

    private Map<String, String> readPreset(String id) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        Reader in = null;
        try {
            if (id.startsWith("b:") && overrideOf(id).exists()) {
                in = new FileReader(overrideOf(id));
            } else if (!id.startsWith("c:")) {
                InputStream a = getAssets().open("presets/" + id.substring(2) + ".conf");
                in = new InputStreamReader(a, "UTF-8");
            } else {
                in = new FileReader(new File(presetsDir, id.substring(2) + ".conf"));
            }
            BufferedReader r = new BufferedReader(in);
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                int eq = line.indexOf('=');
                if (line.startsWith("#") || eq <= 0) continue;
                m.put(line.substring(0, eq), line.substring(eq + 1));
            }
        } catch (IOException e) {
            m.clear();
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) { }
        }
        return m;
    }

    private void applyPreset(String id) {
        Map<String, String> m = readPreset(id);
        if (m.isEmpty()) return;
        cfg = m;
        active = id;
        modified = false;
        selectedBand = 0;
        writeAllSettings();
        saveActive();
    }

    private File overrideOf(String id) {
        return new File(overridesDir, id.substring(2) + ".conf");
    }

    private String confText() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : cfg.entrySet()) b.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        return b.toString().trim();
    }

    private void savePreset(String name) {
        writeFile(new File(presetsDir, name + ".conf"), confText());
        active = "c:" + name;
        modified = false;
        saveActive();
    }

    /** Save: the edits become part of the preset they were made to. */
    private void saveOverActive() {
        writeFile(active.startsWith("c:") ? new File(presetsDir, active.substring(2) + ".conf") : overrideOf(active), confText());
        modified = false;
        saveActive();
    }

    private String subtitleOf(Map<String, String> m) {
        String mode = m.containsKey("mode") ? m.get("mode") : "beat";
        Gauge g = Gauge.of(mode);
        if (g != null) {
            return g.title + (g == Gauge.EFFICIENCY ? ("trip".equals(m.get("eff_source")) ? " (trip)" : " (average)") : "")
                    + " · " + g.show(speedValue(m, g.lowKey, g.lowDef)) + "–" + g.show(speedValue(m, g.highKey, g.highDef)) + " " + g.unit(carUnitsKm());
        }
        if (mode.equals("spectrum")) {
            String bands = m.containsKey("bands") ? m.get("bands") : DEFAULT_BANDS;
            return "Spectrum · " + bands.trim().split("[ ,\\t]+").length + " colours";
        }
        if (mode.equals("pulse")) {
            int pc = colorIndexOf(m.get("pulse_color"), -1, -2);
            return pc >= 0 ? "Pulse · picked color" : pc == -2 ? "Pulse · drive mode color" : "Pulse · car's own color";
        }
        if (mode.equals("duet")) {
            int bed = colorIndexOf(m.get("duet_bed"), -1, -1), acc = colorIndexOf(m.get("duet_accent"), -1, -1);
            return "Duet · " + (bed >= 0 ? "picked bed" : "auto bed") + " + " + (acc >= 0 ? "picked flash" : "opposite flash");
        }
        if (mode.equals("drop")) {
            int st = Math.max(0, Math.min(2, intOf(m.get("drop_style"), 0)));
            String[] names = {"Full Neon", "Cool to Fire", "Cyberpunk"};
            return "Drop · " + names[st] + " build & drop";
        }
        if (mode.equals("synesthesia")) {
            int sh = intOf(m.get("syn_shift"), 0);
            return "Synesthesia · pitch-to-color" + (sh > 0 ? " (+" + sh + "°)" : "");
        }
        if (mode.equals("ember")) {
            int th = Math.max(0, Math.min(2, intOf(m.get("ember_theme"), 0)));
            String[] names = {"Fire", "Plasma", "Aurora"};
            return "Ember · " + names[th] + " beat glow";
        }
        if (mode.equals("drift")) {
            int th = Math.max(0, Math.min(4, intOf(m.get("drift_theme"), 0)));
            int nb = intOf(m.get("drift_beats"), 4);
            String[] names = {"Forest & Aurora", "Cyberpunk", "Sunset", "Ocean Deep", "Full Wheel"};
            return "Drift · " + names[th] + " (" + nb + " beats)";
        }
        if (mode.equals("nightdrive")) {
            return "Night Drive · " + ("speed".equals(m.get("nd_source")) ? "speed" : "power & regen") + " + beat pulse";
        }
        return "Beat · colour changes";
    }

    // ---------- screen ----------

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(8), dp(40), dp(4));   // right: clear of the side panel handle

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        root.addView(left, new LinearLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Light Vibes", 22, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch on = new Switch(this);
        Widgets.teal(on);
        on.setTextOn("On");
        on.setTextOff("Off");
        on.setChecked(!"0".equals(readFirstLine(new File(settingsDir, "enabled"))));
        on.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                writeFile(new File(settingsDir, "enabled"), checked ? "1" : "0");
            }
        });
        head.addView(on);
        left.addView(head);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));   // (the mode line that was here is gone: the mode buttons show it)
        TextView feedback = text("Feedback", 15, ACCENT);
        feedback.setGravity(Gravity.CENTER_VERTICAL);
        feedback.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openFeedback(); }
        });
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        fp.rightMargin = dp(20);
        statusRow.addView(feedback, fp);
        TextView report = text("Report a problem", 15, ACCENT);
        report.setGravity(Gravity.CENTER_VERTICAL);
        report.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openReport(); }
        });
        statusRow.addView(report, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        left.addView(statusRow);

        ScrollView scroll = new ScrollView(this);
        presetList = new LinearLayout(this);
        presetList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(presetList);
        left.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(0, dp(8), 0, 0);
        saveButton = button("Save", false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                saveOverActive();
                refreshPresets();
            }
        });
        View saveAs = button("Save as…", false, new View.OnClickListener() {
            @Override public void onClick(View v) { askSave(); }
        });
        deleteButton = button("Delete", false, new View.OnClickListener() {
            @Override public void onClick(View v) { askDelete(); }
        });
        actions.addView(saveButton, weighted(0, 56, 1, 0));
        actions.addView(saveAs, weighted(0, 56, 1.3f, 6));
        actions.addView(deleteButton, weighted(0, 56, 1, 6));
        left.addView(actions);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        editorTop = new LinearLayout(this);     // stays put while the settings below scroll
        editorTop.setOrientation(LinearLayout.VERTICAL);
        right.addView(editorTop);
        ScrollView rightScroll = new ScrollView(this);
        editor = new LinearLayout(this);
        editor.setOrientation(LinearLayout.VERTICAL);
        rightScroll.addView(editor);
        right.addView(rightScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        rp.leftMargin = dp(20);
        root.addView(right, rp);
        meter = new MeterView(this);

        setContentView(root);
        refreshPresets();
        refreshEditor();
    }

    private LinearLayout.LayoutParams weighted(int w, int h, float weight, int leftMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w == 0 ? 0 : dp(w), h == 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : dp(h), weight);
        p.leftMargin = dp(leftMargin);
        return p;
    }

    private TextView text(String s, float sp, int colour) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(colour);
        return t;
    }

    private GradientDrawable rounded(int fill, int stroke, int strokeDp, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private TextView button(String label, boolean selected, View.OnClickListener click) {
        TextView t = text(label, 18, selected ? ON_TEXT : TEXT);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundDrawable(rounded(selected ? CARD_ON : CARD, 0, 0, 8));
        t.setOnClickListener(click);
        return t;
    }

    private void refreshPresets() {
        presetList.removeAllViews();
        for (final String id : presetIds()) {
            boolean sel = id.equals(active);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(8), dp(14), dp(8));
            row.setBackgroundDrawable(rounded(sel ? CARD_ON : CARD, 0, 0, 8));
            row.addView(text(titleOf(id) + (sel && modified ? " (edited)" : ""), 19, sel ? ON_TEXT : TEXT));
            row.addView(text(subtitleOf(sel ? cfg : readPreset(id)), 14, sel ? ON_TEXT : MUTED));
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (modified) askUnsaved(id);   // edits would be lost: save, save as new, or discard them first
                    else switchTo(id);
                }
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.bottomMargin = dp(6);
            presetList.addView(row, p);
        }
        saveButton.setEnabled(modified);
        saveButton.setAlpha(modified ? 1f : 0.35f);
        boolean removable = presetIds().size() > 1;
        deleteButton.setEnabled(removable);
        deleteButton.setAlpha(removable ? 1f : 0.35f);

        presetList.addView(listButton("+ New preset", new View.OnClickListener() {
            @Override public void onClick(View v) { askNew(); }
        }));
        String[] over = overridesDir.list();
        boolean hasOverrides = false;
        if (over != null) for (String n : over) if (n.endsWith(".conf")) hasOverrides = true;
        if (hasOverrides) {
            presetList.addView(listButton("Reset modified presets", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    String[] names = overridesDir.list();
                    if (names != null) for (String n : names) new File(overridesDir, n).delete();
                    if (active.startsWith("b:")) applyPreset(active);
                    refreshPresets();
                    refreshEditor();
                }
            }));
        }
        String[] hidden = hiddenDir.list();
        if (hidden != null && hidden.length > 0) {
            presetList.addView(listButton("Restore built-in presets", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    String[] names = hiddenDir.list();
                    if (names != null) for (String n : names) new File(hiddenDir, n).delete();
                    refreshPresets();
                }
            }));
        }
    }

    private View listButton(String label, View.OnClickListener click) {
        TextView t = text(label, 18, ACCENT);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundDrawable(rounded(BG, RULE, 1, 8));
        t.setOnClickListener(click);
        t.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        ((LinearLayout.LayoutParams) t.getLayoutParams()).bottomMargin = dp(6);
        return t;
    }

    private String mode() {
        return cfg.containsKey("mode") ? cfg.get("mode") : "beat";
    }

    private void openFeedback() {
        if (feedbackPage != null) return;
        if (reportPage != null) { reportPage.stop(); reportPage = null; }
        feedbackPage = new FeedbackPage(this, mode(), new Runnable() {
            @Override public void run() { closeReport(); }
        });
        refreshEditor();
    }

    private void openReport() {
        if (reportPage != null) return;
        feedbackPage = null;
        reportPage = new ReportPage(this, getFilesDir(), new Runnable() {
            @Override public void run() { closeReport(); }
        });
        refreshEditor();
    }

    /** Back to the settings from Report a problem or Feedback. */
    private void closeReport() {
        if (reportPage == null && feedbackPage == null) return;
        if (reportPage != null) reportPage.stop();
        reportPage = null;
        feedbackPage = null;
        refreshEditor();
    }

    private void refreshEditor() {
        editor.removeAllViews();
        editorTop.removeAllViews();
        tuneButton = null;
        tuneStatus = null;
        if (reportPage != null || feedbackPage != null) {
            speedNow = null;
            editor.addView(reportPage != null ? reportPage : feedbackPage);
            return;
        }
        editorTop.addView(modeRow());
        editorTop.addView(pageTabs());
        String mode = mode();
        speedNow = null;
        syncMeter();
        Gauge g = Gauge.of(mode);
        if (g != null) {
            // the car's units, not a choice: older settings said mph/kmh or mi/km
            if (g.unitKey != null && !"auto".equals(cfg.get(g.unitKey))) {
                cfg.put(g.unitKey, "auto");
                writeFile(new File(settingsDir, g.unitKey), "auto");
            }
            if (tab == TAB_COLOR) addGaugeColour(g);
            else if (tab == TAB_BRIGHTNESS) addGaugeBrightness();
            else addGaugeTuning(g);
            return;
        }
        if (tab == TAB_TUNING) {
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(84));
            mp.topMargin = dp(8);
            editorTop.addView(meter, mp);
            configureMeter();
            addResponse();
            return;
        }
        if (tab == TAB_BRIGHTNESS) {
            if (mode.equals("pulse") || mode.equals("duet") || mode.equals("drop") || mode.equals("ember") || mode.equals("nightdrive")) {
                addBrightness();
                return;
            }
            addBrightnessChoice();
            if ("1".equals(cfg.get("brightness_fixed"))) addGaugeBrightness();   // one level, and Dimmed in Dark
            else addBrightness();                                         // a range (flashing is a radio choice)
            return;
        }
        if (mode.equals("spectrum")) {
            addSpectrum();
        } else if (mode.equals("beat")) {
            addNote("The colour moves around the wheel on every beat. A larger step makes bigger jumps.");
            addStepSlider();
        } else if (mode.equals("duet")) {
            addDuetColour();
        } else if (mode.equals("drop")) {
            addDropColour();
        } else if (mode.equals("synesthesia")) {
            addSynesthesiaColour();
        } else if (mode.equals("ember")) {
            addEmberColour();
        } else if (mode.equals("drift")) {
            addDriftColour();
        } else if (mode.equals("nightdrive")) {
            addNightDriveColour();
        } else {
            addPulseColour();
        }
    }

    /**
     * Pulse's Color tab: the car's own colour, or a picked one; and a switch that makes the picked one the car's ambient
     * colour too (it then stays after the music, as if picked in Settings > Vehicle > Lights > Ambient light).
     */
    /** Spectrum, Beat, Synesthesia and Drift: brightness follows the music, or stays at one level. */
    private void addBrightnessChoice() {
        TextView title = text("Brightness", 16, MUTED);
        title.setPadding(0, dp(12), 0, dp(2));
        editor.addView(title);
        boolean fixed = "1".equals(cfg.get("brightness_fixed"));
        addRadios(2000, new String[][] {{"0", "Follow the music"}, {"1", "Fixed level"}}, fixed ? "1" : "0", "brightness_fixed");
        // following the music: by the average loudness alone, or with a flash to the top of the range on each beat
        if (!fixed) addRadios(2100, new String[][] {{"0", "Average loudness"}, {"1", "Flash on beat"}}, flashOn() ? "1" : "0", "flash_on_beat");
    }

    /** A row of radio buttons for one setting; picking one saves it and redraws the page. */
    private void addRadios(int idBase, String[][] options, String current, final String key) {
        android.widget.RadioGroup group = new android.widget.RadioGroup(this);
        group.setOrientation(LinearLayout.HORIZONTAL);
        final String[] now = {current};
        for (int i = 0; i < options.length; i++) {
            final String v = options[i][0];
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            Widgets.teal(rb);
            rb.setId(idBase + i);
            rb.setText(options[i][1]);
            rb.setTextSize(18);
            rb.setTextColor(TEXT);
            rb.setChecked(v.equals(current));
            rb.setPadding(rb.getPaddingLeft() + dp(6), 0, dp(24), 0);
            rb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                    if (!checked || v.equals(now[0])) return;
                    now[0] = v;
                    set(key, v);
                    refreshEditor();
                }
            });
            group.addView(rb, new android.widget.RadioGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        }
        editor.addView(group);
    }

    private void addPulseColour() {
        final int picked = colorIndexOf(cfg.get("pulse_color"), -1, -2);
        // the "Pulse" colour card, as on the other modes: the picked colour, or the car's own (as lightvibesd last read it)
        int shown = picked >= 0 ? wheelColour(picked) : picked == -1 ? carColour() : 0;
        LinearLayout row = new LinearLayout(this);
        row.setBaselineAligned(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setBackgroundDrawable(rounded(CARD, TEXT, 3, 8));
        cell.setPadding(dp(4), dp(6), dp(4), dp(6));
        View swatch = new View(this);
        if (picked == -2) {      // the car picks it by drive mode: a strip of drive-mode-like colours
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, DRIVE_MODE_STRIP);
            g.setCornerRadius(dp(6));
            swatch.setBackgroundDrawable(g);
        } else {
            swatch.setBackgroundDrawable(shown != 0 ? rounded(shown, 0, 0, 6) : rounded(CARD, OUTLINE, 1, 6));
        }
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26));
        sp.leftMargin = sp.rightMargin = dp(6);
        sp.bottomMargin = dp(3);
        cell.addView(swatch, sp);
        TextView name = text("Pulse", 16, TEXT);
        name.setGravity(Gravity.CENTER);
        cell.addView(name);
        row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        // the card is as wide as Low Efficiency's (half the row, less the gap); the switch takes the other half
        LinearLayout side = new LinearLayout(this);
        side.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sdp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        sdp.leftMargin = dp(8);
        row.addView(side, sdp);
        TextView t = text("Set as ambient light color", 16, picked < 0 ? MUTED : TEXT);
        t.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);      // right next to its switch
        side.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch sw = new Switch(this);
        Widgets.teal(sw);
        sw.setChecked("1".equals(cfg.get("pulse_set_car")));
        sw.setEnabled(picked >= 0);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) { set("pulse_set_car", checked ? "1" : "0"); }
        });
        LinearLayout.LayoutParams swp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        swp.leftMargin = dp(10);
        side.addView(sw, swp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(10);
        editor.addView(row, rp);
        addPalette(picked, new ColourPick() {
            @Override public void pick(int idx) {
                set("pulse_color", String.valueOf(idx));
                refreshEditor();
            }
        });
        // the last choice in the picker: the car's own drive-mode colour (it changes with Eco, Normal, Sport...)
        TextView drive = text("Drive Mode", 16, 0xFFFFFFFF);
        drive.setTypeface(Typeface.DEFAULT_BOLD);
        drive.setGravity(Gravity.CENTER);
        drive.setShadowLayer(dp(2), 0, 0, 0x99000000);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, DRIVE_MODE_STRIP);
        g.setCornerRadius(dp(5));
        if (picked == -2) g.setStroke(dp(3), TEXT);
        drive.setBackgroundDrawable(g);
        drive.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                set("pulse_color", "-2");     // turns the car's drive-mode colour on, also when the mode changes mid-song
                refreshEditor();
            }
        });
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        dlp.leftMargin = dlp.rightMargin = dp(2);
        dlp.topMargin = dp(4);
        editor.addView(drive, dlp);
    }

    /** Drive Mode's look: three equal parts, cyan, blue and red, blended. */
    private static final int[] DRIVE_MODE_STRIP = {kiaColour(54), kiaColour(0), kiaColour(22)};
    private static int kiaColour(int i) { return 0xFF000000 | (KIA_R[i] << 16) | (KIA_G[i] << 8) | KIA_B[i]; }

    /** The car's own ambient colour, as lightvibesd last read it ("r g b drive_mode"); 0x01xx stock colours mask & 0xFF. */
    private static int carColour() {
        String v = readFirstLine(new File("/data/hmgapi/public/light-vibes/user_color"));
        if (v == null) return 0;
        String[] p = v.split(" ");
        if (p.length < 3) return 0;
        return 0xFF000000 | ((intOf(p[0], 0) & 0xFF) << 16) | ((intOf(p[1], 0) & 0xFF) << 8) | (intOf(p[2], 0) & 0xFF);
    }

    private static String shippedForMode(String m) {
        if ("spectrum".equals(m)) return "intense";
        if ("beat".equals(m)) return "lively";
        if ("pulse".equals(m)) return "glow";
        return m;
    }

    private TextView modeButton(String label, boolean selected, View.OnClickListener click) {
        TextView t = text(label, 15, selected ? ON_TEXT : TEXT);
        t.setSingleLine(true);
        t.setPadding(dp(4), 0, dp(4), 0);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundDrawable(rounded(selected ? CARD_ON : CARD, 0, 0, 8));
        t.setOnClickListener(click);
        return t;
    }

    private View modeRow() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        String[][][] rows = {
            {{"spectrum", "Spectrum"}, {"beat", "Beat"}, {"pulse", "Pulse"}, {"duet", "Duet"}, {"drop", "Drop"}, {"synesthesia", "Synesthesia"}},
            {{"ember", "Ember"}, {"drift", "Drift"}, {"nightdrive", "Night Drive"}, {"speed", "Speed"}, {"power", "Power"}, {"efficiency", "Efficiency"}}
        };
        String current = mode();
        for (int r = 0; r < rows.length; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setBaselineAligned(false);
            if (r > 0) row.setPadding(0, dp(4), 0, 0);
            for (int i = 0; i < rows[r].length; i++) {
                final String m = rows[r][i][0];
                row.addView(modeButton(rows[r][i][1], m.equals(current), new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (m.equals(mode())) return;
                        speedEnd = 0;
                        duetEnd = 0;
                        if (m.equals("spectrum") && !cfg.containsKey("bands")) {
                            cfg.put("bands", DEFAULT_BANDS);
                            writeFile(new File(settingsDir, "bands"), DEFAULT_BANDS);
                        }
                        for (Map.Entry<String, String> e : readShipped(shippedForMode(m)).entrySet()) {
                            if (e.getKey().equals("mode") || cfg.containsKey(e.getKey())) continue;
                            cfg.put(e.getKey(), e.getValue());
                            writeFile(new File(settingsDir, e.getKey()), e.getValue());
                        }
                        set("mode", m);
                        refreshEditor();
                    }
                }), weighted(0, 40, 1, i == 0 ? 0 : 6));
            }
            box.addView(row);
        }
        return box;
    }

    /** Every mode's three tabs, under the mode row: an underline marks the open one. */
    private View pageTabs() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(6), 0, 0);
        LinearLayout row = new LinearLayout(this);
        row.setBaselineAligned(false);
        row.addView(tab("Color", TAB_COLOR), new LinearLayout.LayoutParams(dp(140), dp(42)));
        row.addView(tab("Brightness", TAB_BRIGHTNESS), new LinearLayout.LayoutParams(dp(140), dp(42)));
        row.addView(tab("Tuning", TAB_TUNING), new LinearLayout.LayoutParams(dp(140), dp(42)));
        box.addView(row);
        View rule = new View(this);
        rule.setBackgroundColor(RULE);
        box.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return box;
    }

    private View tab(String label, final int which) {
        boolean sel = tab == which;
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(label, 18, sel ? TEXT : MUTED);
        name.setGravity(Gravity.CENTER);
        if (sel) name.setTypeface(Typeface.DEFAULT_BOLD);
        t.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        View line = new View(this);
        line.setBackgroundColor(sel ? ACCENT : 0x00000000);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        lp.leftMargin = lp.rightMargin = dp(12);
        t.addView(line, lp);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (tab == which) return;
                tab = which;
                refreshEditor();
                syncMeter();
            }
        });
        return t;
    }

    private List<Band> bands() {
        List<Band> out = new ArrayList<Band>();
        String txt = cfg.containsKey("bands") ? cfg.get("bands") : DEFAULT_BANDS;
        for (String tok : txt.trim().split("[ ,\\t]+")) {
            Band b = Band.parse(tok);
            if (b != null) out.add(b);
        }
        return out;
    }

    private void addSpectrum() {
        final List<Band> bands = bands();
        if (selectedBand >= bands.size()) selectedBand = 0;

        editor.addView(new View(this), new LinearLayout.LayoutParams(1, dp(10)));   // the same gap as the other Color tabs
        addBandChips(bands, 26, true);

        addPalette(bands.isEmpty() ? -1 : bands.get(selectedBand).wheelIndex(), new ColourPick() {
            @Override public void pick(int idx) {
                if (bands.isEmpty()) return;
                bands.get(selectedBand).colour = String.valueOf(idx);
                saveBands(bands);
                refreshEditor();
            }
        });
    }

    private void addDuetColour() {
        if (duetEnd < 0 || duetEnd > 1) duetEnd = 0;
        final int bed = colorIndexOf(cfg.get("duet_bed"), -1, -1);
        final int acc = colorIndexOf(cfg.get("duet_accent"), -1, -1);
        int baseBed = bed >= 0 ? bed : vividAtHue(178.0);
        int shownAcc = acc >= 0 ? acc : vividAtHue(wheelHue(baseBed) + 180.0);

        LinearLayout ends = new LinearLayout(this);
        ends.setBaselineAligned(false);
        String[] titles = {"Melody Bed" + (bed < 0 ? " (auto)" : ""), "Beat Accent" + (acc < 0 ? " (opposite)" : "")};
        for (int i = 0; i < 2; i++) {
            final int which = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setBackgroundDrawable(rounded(CARD, i == duetEnd ? TEXT : CARD, 3, 8));
            cell.setPadding(dp(4), dp(6), dp(4), dp(6));
            View swatch = new View(this);
            if (i == 0 && bed < 0) {
                int[] autoCols = {wheelColour(vividAtHue(178.0)), wheelColour(vividAtHue(209.0)), wheelColour(vividAtHue(92.0)), wheelColour(vividAtHue(268.0))};
                GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, autoCols);
                g.setCornerRadius(dp(6));
                swatch.setBackgroundDrawable(g);
            } else {
                swatch.setBackgroundDrawable(rounded(wheelColour(i == 0 ? bed : shownAcc), 0, 0, 6));
            }
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26));
            sp.leftMargin = sp.rightMargin = dp(6);
            sp.bottomMargin = dp(3);
            cell.addView(swatch, sp);
            TextView name = text(titles[i], 16, TEXT);
            name.setGravity(Gravity.CENTER);
            cell.addView(name);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    duetEnd = which;
                    refreshEditor();
                }
            });
            ends.addView(cell, weighted(0, 0, 1, i == 0 ? 0 : 8));
        }
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ep.topMargin = dp(10);
        editor.addView(ends, ep);

        final String targetKey = duetEnd == 0 ? "duet_bed" : "duet_accent";
        final int curIdx = duetEnd == 0 ? bed : acc;
        addPalette(curIdx, new ColourPick() {
            @Override public void pick(int idx) {
                set(targetKey, String.valueOf(idx));
                refreshEditor();
            }
        });

        TextView autoBtn = text(duetEnd == 0 ? "Auto Drift Bed (rotates every 8 beats)" : "Auto Opposite Accent (180° complement)", 15, curIdx < 0 ? ON_TEXT : TEXT);
        autoBtn.setGravity(Gravity.CENTER);
        autoBtn.setBackgroundDrawable(rounded(curIdx < 0 ? CARD_ON : CARD, curIdx < 0 ? TEXT : OUTLINE, curIdx < 0 ? 2 : 1, 6));
        autoBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                set(targetKey, "-1");
                refreshEditor();
            }
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        alp.leftMargin = alp.rightMargin = dp(2);
        alp.topMargin = dp(4);
        editor.addView(autoBtn, alp);
    }

    private View swatchStripView(int[] wheelIndices, int heightDp) {
        LinearLayout strip = new LinearLayout(this);
        for (int i = 0; i < wheelIndices.length; i++) {
            View seg = new View(this);
            seg.setBackgroundColor(wheelColour(wheelIndices[i]));
            strip.addView(seg, new LinearLayout.LayoutParams(0, dp(heightDp), 1));
        }
        return strip;
    }

    private void addDropColour() {
        addNote("Follows song dynamics: calm cool simmer in breakdowns, rising tension on build-ups, and bold neon leaps when the drop hits.");
        final int cur = Math.max(0, Math.min(2, intOf(cfg.get("drop_style"), 0)));
        String[] names = {"Full Neon", "Cool to Fire", "Cyberpunk"};
        double[][] hues = {
            {0.0, 40.0, 315.0, 92.0, 185.0, 288.0},
            {0.0, 20.0, 40.0, 340.0, 12.0, 315.0},
            {355.0, 315.0, 288.0, 185.0, 235.0, 335.0}
        };
        LinearLayout row = new LinearLayout(this);
        row.setBaselineAligned(false);
        for (int i = 0; i < 3; i++) {
            final int style = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setBackgroundDrawable(rounded(CARD, i == cur ? TEXT : CARD, 3, 8));
            cell.setPadding(dp(8), dp(8), dp(8), dp(8));
            int[] cols = new int[6];
            for (int k = 0; k < 6; k++) cols[k] = vividAtHue(hues[i][k]);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24));
            sp.bottomMargin = dp(6);
            cell.addView(swatchStripView(cols, 24), sp);
            TextView title = text(names[i], 16, TEXT);
            title.setGravity(Gravity.CENTER);
            cell.addView(title);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    set("drop_style", String.valueOf(style));
                    refreshEditor();
                }
            });
            row.addView(cell, weighted(0, 0, 1, i == 0 ? 0 : 8));
        }
        editor.addView(row);
    }

    private void addSynesthesiaColour() {
        addNote("Maps musical pitch continuously to color: deep bass at the left, vocals and guitars in the middle, bright highs at the right.");
        int shift0 = Math.max(0, Math.min(355, intOf(cfg.get("syn_shift"), 0)));
        LinearLayout labels = new LinearLayout(this);
        TextView lBass = text("Bass", 14, MUTED), lMid = text("Mids / Vocals", 14, MUTED), lHigh = text("Highs", 14, MUTED);
        lMid.setGravity(Gravity.CENTER);
        lHigh.setGravity(Gravity.RIGHT);
        labels.addView(lBass, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        labels.addView(lMid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        labels.addView(lHigh, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        editor.addView(labels);

        final LinearLayout strip = new LinearLayout(this);
        strip.setPadding(0, dp(4), 0, dp(8));
        for (int i = 0; i < 32; i++) strip.addView(new View(this), new LinearLayout.LayoutParams(0, dp(24), 1));
        drawSynStrip(strip, shift0);
        editor.addView(strip);

        final TextView label = text("Color rotation: " + shift0 + "°", 16, TEXT);
        RangeSlider bar = level(0, 355, 5, shift0);
        bar.setListener(new RangeSlider.Listener() {
            @Override public void moved(int lo, int hi) {
                label.setText("Color rotation: " + hi + "°");
                drawSynStrip(strip, hi);
            }
            @Override public void done(int lo, int hi) {
                set("syn_shift", String.valueOf(hi));
            }
        });
        editor.addView(sliderRow(label, 185, bar, null, null));
    }

    private static void drawSynStrip(LinearLayout strip, int shift) {
        int n = strip.getChildCount();
        for (int i = 0; i < n; i++) {
            double h = (shift + (i / (double) (n - 1)) * 285.0) % 360.0;
            strip.getChildAt(i).setBackgroundColor(wheelColour(vividAtHue(h)));
        }
    }

    private static int emberWheelAt(int theme, double heat) {
        double[][] stops = {
            {268.0, 355.0, 18.0, 40.0},
            {250.0, 292.0, 210.0, 182.0},
            {225.0, 178.0, 92.0, 40.0}
        };
        double[] s = stops[Math.max(0, Math.min(2, theme))];
        double h;
        if (heat < 0.33) h = hueLerp(s[0], s[1], heat / 0.33);
        else if (heat < 0.72) h = hueLerp(s[1], s[2], (heat - 0.33) / 0.39);
        else h = hueLerp(s[2], s[3], (heat - 0.72) / 0.28);
        return vividAtHue(h);
    }

    private void addEmberColour() {
        addNote("Each beat ignites a bright hot flare (right) that cools smoothly through rich glowing embers (left) as the note decays.");
        final int cur = Math.max(0, Math.min(2, intOf(cfg.get("ember_theme"), 0)));
        String[] names = {"Fire", "Plasma", "Aurora"};
        LinearLayout row = new LinearLayout(this);
        row.setBaselineAligned(false);
        for (int i = 0; i < 3; i++) {
            final int th = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setBackgroundDrawable(rounded(CARD, i == cur ? TEXT : CARD, 3, 8));
            cell.setPadding(dp(8), dp(8), dp(8), dp(8));
            int[] cols = new int[16];
            for (int k = 0; k < 16; k++) cols[k] = emberWheelAt(i, k / 15.0);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24));
            sp.bottomMargin = dp(6);
            cell.addView(swatchStripView(cols, 24), sp);
            TextView title = text(names[i], 16, TEXT);
            title.setGravity(Gravity.CENTER);
            cell.addView(title);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    set("ember_theme", String.valueOf(th));
                    refreshEditor();
                }
            });
            row.addView(cell, weighted(0, 0, 1, i == 0 ? 0 : 8));
        }
        editor.addView(row);
    }

    private void addDriftColour() {
        addNote("Holds each curated mood color across whole musical phrases, shifting cleanly on bar boundaries.");
        final int cur = Math.max(0, Math.min(4, intOf(cfg.get("drift_theme"), 0)));
        String[] names = {"Forest & Aurora", "Cyberpunk", "Sunset", "Ocean Deep", "Full Wheel"};
        double[][] themes = {
            {92.0, 178.0, 209.0, 268.0, 145.0},
            {186.0, 236.0, 290.0, 330.0, 356.0},
            {40.0, 16.0, 356.0, 316.0, 270.0},
            {176.0, 192.0, 209.0, 232.0, 256.0},
            {0.0, 72.0, 144.0, 216.0, 288.0}
        };
        LinearLayout row = new LinearLayout(this);
        row.setBaselineAligned(false);
        for (int i = 0; i < 5; i++) {
            final int th = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setBackgroundDrawable(rounded(CARD, i == cur ? TEXT : CARD, 3, 8));
            cell.setPadding(dp(6), dp(8), dp(6), dp(8));
            int[] cols = new int[5];
            for (int k = 0; k < 5; k++) cols[k] = vividAtHue(themes[i][k]);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22));
            sp.bottomMargin = dp(5);
            cell.addView(swatchStripView(cols, 22), sp);
            TextView title = text(names[i], 14, TEXT);
            title.setGravity(Gravity.CENTER);
            cell.addView(title);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    set("drift_theme", String.valueOf(th));
                    refreshEditor();
                }
            });
            row.addView(cell, weighted(0, 0, 1, i == 0 ? 0 : 6));
        }
        editor.addView(row);

        int nb = Math.max(2, Math.min(16, intOf(cfg.get("drift_beats"), 4)));
        final TextView label = text("Beats per color: " + nb, 16, TEXT);
        RangeSlider bar = level(2, 16, 1, nb);
        bar.setListener(new RangeSlider.Listener() {
            @Override public void moved(int lo, int hi) { label.setText("Beats per color: " + hi); }
            @Override public void done(int lo, int hi) { set("drift_beats", String.valueOf(hi)); }
        });
        editor.addView(sliderRow(label, 185, bar, null, null));
    }

    private void addNightDriveColour() {
        boolean bySpeed = "speed".equals(cfg.get("nd_source"));
        TextView title = text("Color Driven By", 15, MUTED);
        title.setPadding(0, dp(8), 0, 0);
        editor.addView(title);
        addRadios(2400, new String[][] {{"power", "Motor Power & Regen"}, {"speed", "Vehicle Speed"}}, bySpeed ? "speed" : "power", "nd_source");
        addGaugeColour(bySpeed ? Gauge.SPEED : Gauge.POWER);
    }

    private interface ColourPick {
        void pick(int wheelIndex);
    }

    private void addPalette(final int current, final ColourPick listener) {
        addPalette(current, listener, 26, 8);      // rows as tall as the colour on the buttons above
    }

    private void addPalette(final int current, final ColourPick listener, int swatchDp, int topDp) {
        boolean dimPale = Gauge.of(mode()) == null && !"nightdrive".equals(mode());
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(topDp), 0, 0);
        if (WHEEL_R.length > 64) {
            TextView stLbl = text("Stock ambient colors", 13, MUTED);
            stLbl.setPadding(dp(2), 0, 0, dp(2));
            grid.addView(stLbl);
            LinearLayout stRow = new LinearLayout(this);
            for (int idx = 64; idx < WHEEL_R.length; idx++) {
                final int pickIdx = idx;
                View v = new View(this);
                v.setBackgroundDrawable(rounded(wheelColour(idx), TEXT, idx == current ? 3 : 0, 5));
                if (dimPale && idx < 67 && idx != current) v.setAlpha(0.55f);
                v.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) { listener.pick(pickIdx); }
                });
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(swatchDp), 1);
                p.leftMargin = p.rightMargin = dp(2);
                p.topMargin = p.bottomMargin = dp(2);
                stRow.addView(v, p);
            }
            grid.addView(stRow);
            TextView whLbl = text("Custom color wheel", 13, MUTED);
            whLbl.setPadding(dp(2), dp(4), 0, dp(2));
            grid.addView(whLbl);
        }
        for (int r = 0; r < 4; r++) {
            LinearLayout line = new LinearLayout(this);
            for (int c = 0; c < 16; c++) {
                final int idx = r * 16 + c;
                View v = new View(this);
                v.setBackgroundDrawable(rounded(wheelColour(idx), TEXT, idx == current ? 3 : 0, 5));
                if (dimPale && !vividWheel(idx) && idx != current) v.setAlpha(0.55f);
                v.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) { listener.pick(idx); }
                });
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(swatchDp), 1);
                p.leftMargin = p.rightMargin = dp(2);
                p.topMargin = p.bottomMargin = dp(2);
                line.addView(v, p);
            }
            grid.addView(line);
        }
        editor.addView(grid);
    }

    private void saveBands(List<Band> bands) {
        StringBuilder sb = new StringBuilder();
        for (Band b : bands) sb.append(sb.length() > 0 ? " " : "").append(b);
        set("bands", sb.toString());
    }

    /** Two bands with the same name get numbered, so they can be told apart. */
    private static String[] titlesOf(List<Band> bands) {
        String[] out = new String[bands.size()];
        for (int i = 0; i < out.length; i++) {
            String t = bands.get(i).title();
            int same = 0, position = 0;
            for (int j = 0; j < out.length; j++) {
                if (bands.get(j).title().equals(t)) {
                    same++;
                    if (j <= i) position++;
                }
            }
            out[i] = same > 1 ? t + " " + position : t;
        }
        return out;
    }

    private static boolean canSplit(Band b) {
        return intOf(b.hi, 0) - intOf(b.lo, 0) >= MIN_SPLIT_HZ;
    }

    /** A new band is the top half of the selected one, so all the sound stays covered. */
    private void addBand(List<Band> bands) {
        Band b = bands.get(selectedBand);
        int lo = Math.max(intOf(b.lo, 0), 20), hi = intOf(b.hi, 0);
        int mid = Math.round((float) Math.sqrt(lo * (double) hi) / 10) * 10;
        mid = Math.max(lo + MIN_SPLIT_HZ / 2, Math.min(hi - MIN_SPLIT_HZ / 2, mid));
        Band n = new Band();
        n.lo = String.valueOf(mid);
        n.hi = b.hi;
        n.boost = b.boost;
        n.colour = String.valueOf(unusedColour(bands, b.wheelIndex()));
        b.hi = n.lo;
        bands.add(selectedBand + 1, n);
        selectedBand++;
        saveBands(bands);
        refreshEditor();
    }

    /** Steps round the wheel from the starting colour, choosing only vivid unused swatches. */
    private static int unusedColour(List<Band> bands, int from) {
        for (int k = 1; k < 64; k++) {
            int c = (from + k * 9) % 64;
            if (!vividWheel(c)) continue;
            boolean used = false;
            for (Band b : bands) if (b.wheelIndex() == c) used = true;
            if (!used) return c;
        }
        return from;
    }

    /** Its neighbour takes over the removed band's range. */
    private void removeBand(List<Band> bands) {
        Band b = bands.get(selectedBand);
        if (selectedBand > 0) bands.get(selectedBand - 1).hi = b.hi;
        else bands.get(1).lo = b.lo;
        bands.remove(selectedBand);
        selectedBand = Math.max(0, selectedBand - 1);
        saveBands(bands);
        refreshEditor();
    }

    private View addRemoveButtons(final List<Band> bands) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView add = button("+", false, new View.OnClickListener() {
            @Override public void onClick(View v) { addBand(bands); }
        });
        TextView remove = button("−", false, new View.OnClickListener() {
            @Override public void onClick(View v) { removeBand(bands); }
        });
        add.setTextSize(24);
        remove.setTextSize(24);
        boolean canAdd = bands.size() < MAX_BANDS && canSplit(bands.get(selectedBand));
        boolean canRemove = bands.size() > 1;
        add.setEnabled(canAdd);
        add.setAlpha(canAdd ? 1f : 0.35f);
        remove.setEnabled(canRemove);
        remove.setAlpha(canRemove ? 1f : 0.35f);
        col.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        rp.topMargin = dp(4);
        col.addView(remove, rp);
        return col;
    }

    private void addBandChips(final List<Band> bands, int swatchDp, boolean editable) {
        String[] titles = titlesOf(bands);
        LinearLayout row = new LinearLayout(this);
        for (int i = 0; i < bands.size(); i++) {
            final int index = i;
            Band b = bands.get(i);
            boolean sel = i == selectedBand;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setBackgroundDrawable(rounded(CARD, sel ? TEXT : CARD, 3, 8));
            cell.setPadding(dp(4), dp(6), dp(4), dp(6));
            View swatch = new View(this);
            swatch.setBackgroundDrawable(rounded(b.rgb(), 0, 0, 6));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(swatchDp));
            sp.leftMargin = sp.rightMargin = dp(6);
            sp.bottomMargin = dp(3);
            cell.addView(swatch, sp);
            TextView name = text(titles[i], bands.size() > 6 ? 14 : 16, TEXT);
            name.setGravity(Gravity.CENTER);
            cell.addView(name);
            TextView range = text(b.range(), bands.size() > 6 ? 10 : 12, MUTED);
            range.setGravity(Gravity.CENTER);
            cell.addView(range);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    selectedBand = index;
                    refreshEditor();
                }
            });
            row.addView(cell, weighted(0, 0, 1, i == 0 ? 0 : 6));
        }
        if (editable && !bands.isEmpty()) {
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.MATCH_PARENT);
            bp.leftMargin = dp(8);
            row.addView(addRemoveButtons(bands), bp);
        }
        editor.addView(row);
    }

    /** A Tuning section's label: short, in the accent colour, so the sliders under it read as a group. */
    private void addSection(String s) {
        TextView t = text(s, 15, ACCENT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(14), 0, dp(2));
        editor.addView(t);
    }

    private void addHeading(String s) {
        TextView t = text(s, 15, MUTED);
        t.setPadding(0, dp(6), 0, dp(4));
        editor.addView(t);
    }

    private void addNote(String s) {
        TextView t = text(s, 17, TEXT);
        t.setPadding(0, dp(16), 0, dp(8));
        editor.addView(t);
    }

    private void addStepSlider() {
        int step = intOf(cfg.get("beat_step"), 19);
        final TextView label = text("Color step: " + step, 16, TEXT);
        RangeSlider bar = level(1, 32, 1, step);
        bar.setListener(new RangeSlider.Listener() {
            @Override public void moved(int lo, int hi) { label.setText("Color step: " + hi); }
            @Override public void done(int lo, int hi) { set("beat_step", String.valueOf(hi)); }
        });
        editor.addView(sliderRow(label, 140, bar, null, null));
    }

    private static String brightLabel(int lo, int hi) {
        return "Brightness Range: " + (lo == 0 ? "0 (off)" : String.valueOf(lo)) + " to " + hi;
    }

    /** The music modes' brightness range, in one row like the other modes' brightness, with Flash on Beat where it applies. Levels 0-10 (0 turns the lamp off between beats; 1-10 match the car's menu). */
    private void addBrightness() {
        final int low = Math.max(0, Math.min(10, intOf(cfg.get("brightness_min"), 1)));
        final int high = Math.max(Math.max(1, low), Math.min(10, intOf(cfg.get("brightness_max"), 10)));
        final TextView label = text(brightLabel(low, high), 16, TEXT);
        RangeSlider range = new RangeSlider(this, 0, 10, 1, low, high, TRACK, ACCENT, TEXT).gap(0);
        range.setListener(new RangeSlider.Listener() {
            @Override public void moved(int lo, int hi) {
                int h = Math.max(1, hi);
                label.setText(brightLabel(Math.min(lo, h), h));
            }
            @Override public void done(int lo, int hi) {
                int h = Math.max(1, hi), l = Math.min(lo, h);
                cfg.put("brightness_max", String.valueOf(h));
                writeFile(new File(settingsDir, "brightness_max"), cfg.get("brightness_max"));
                set("brightness_min", String.valueOf(l));
                if (hi < 1) refreshEditor();
            }
        });
        // every mode has the car's own "dimmed in dark" at the end of its brightness row (Spectrum/Beat choose flashing above)
        Switch sw = new Switch(this);
        Widgets.teal(sw);
        sw.setChecked("1".equals(cfg.get("gauge_dim")));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) { set("gauge_dim", checked ? "1" : "0"); }
        });
        editor.addView(sliderRow(label, 245, range, "Dimmed in Dark", sw));
    }

    // ---------- response page: the same settings the presets were tuned with ----------

    private boolean flashOn() {
        String m = mode();
        if (m.equals("duet") || m.equals("drop")) return true;
        String v = cfg.get("flash_on_beat");
        return v != null && !v.equals("-1") ? !v.equals("0") : m.equals("beat");
    }

    /** Spectrum, Beat, Synesthesia or Drift at one brightness level: loudness and flashes then don't touch the brightness. */
    private boolean fixedLevel() {
        String m = mode();
        return "1".equals(cfg.get("brightness_fixed")) && (m.equals("spectrum") || m.equals("beat") || m.equals("synesthesia") || m.equals("drift"));
    }

    private boolean pulseLike() {
        String m = mode();
        return m.equals("pulse") || m.equals("ember") || m.equals("nightdrive");
    }

    /** Beats flash the brightness: Flash on Beat is on and the brightness follows the music. */
    private boolean flashing() {
        return flashOn() && !fixedLevel() && !pulseLike();
    }

    private boolean usesLoudness() {
        String m = mode();
        return (m.equals("spectrum") || m.equals("beat") || m.equals("synesthesia") || m.equals("drift")) && !fixedLevel() && !flashing();
    }

    /** Beats matter: they move Beat/Duet/Drop/Drift's colour, Pulse/Ember/Night Drive's brightness, or a flash. */
    private boolean beatsMatter() {
        String m = mode();
        return (!m.equals("spectrum") && !m.equals("synesthesia")) || flashing();
    }

    // ---------- speed mode ----------

    /**
     * Speed, Power and Efficiency: the colour follows one reading from the car, between a colour for a low value and one
     * for a high value (Power has a third, for zero). Values are stored as whole numbers: mph or km/h, kW, and tenths of
     * mi/kWh or km/kWh. Speed and Efficiency are in the car's own units: lightvibesd reads them from the car.
     */
    private static final class Gauge {
        static final Gauge SPEED = new Gauge("speed", "Speed", "speed", 0, 80, 44, 22, 0, 160, 5, 1, "speed_unit",
                new String[] {"Low Speed", "High Speed"}, "Speed Range");
        static final Gauge POWER = new Gauge("power", "Power", "power", -50, 150, 0, 22, -150, 350, 10, 1, null,
                new String[] {"Regen", "Zero", "Full Power"}, "Power Range");
        static final Gauge EFFICIENCY = new Gauge("efficiency", "Efficiency", "eff", 20, 45, 22, 44, 0, 100, 1, 10, "eff_unit",
                new String[] {"Low Efficiency", "High Efficiency"}, "Efficiency Range");
        static final String ZERO_KEY = "power_color_zero";
        static final int ZERO_DEF = 44;

        final String mode, title, lowKey, highKey, colLowKey, colHighKey, unitKey, rangeTitle;
        final String[] colourNames;
        final int lowDef, highDef, colLowDef, colHighDef, min, max, step, divisor;

        Gauge(String mode, String title, String prefix, int lowDef, int highDef, int colLowDef, int colHighDef, int min, int max, int step,
              int divisor, String unitKey, String[] colourNames, String rangeTitle) {
            this.mode = mode; this.title = title;
            lowKey = prefix + "_low"; highKey = prefix + "_high";
            colLowKey = prefix + "_color_low"; colHighKey = prefix + "_color_high";
            this.lowDef = lowDef; this.highDef = highDef; this.colLowDef = colLowDef; this.colHighDef = colHighDef;
            this.min = min; this.max = max; this.step = step; this.divisor = divisor;
            this.unitKey = unitKey; this.colourNames = colourNames; this.rangeTitle = rangeTitle;
        }

        static Gauge of(String mode) {
            return "speed".equals(mode) ? SPEED : "power".equals(mode) ? POWER : "efficiency".equals(mode) ? EFFICIENCY : null;
        }

        String unit(boolean km) {
            return this == POWER ? "kW" : this == SPEED ? (km ? "km/h" : "mph") : (km ? "km/kWh" : "mi/kWh");
        }

        String show(int stored) {
            return divisor == 1 ? String.valueOf(stored) : String.valueOf(stored / (double) divisor);
        }

        /** The colour keys and defaults in order along the gauge. */
        String[] colourKeys() {
            return this == POWER ? new String[] {colLowKey, ZERO_KEY, colHighKey} : new String[] {colLowKey, colHighKey};
        }

        int[] colourDefaults() {
            return this == POWER ? new int[] {colLowDef, ZERO_DEF, colHighDef} : new int[] {colLowDef, colHighDef};
        }

        /** The wheel colour for x (0 at the low value, 1 at the high), as lightvibesd picks it. */
        int colourAt(int[] colours, int low, int high, double x) {
            if (this != POWER) return wheelBetween(colours[0], colours[1], x);
            double v = low + (high - low) * x, zero = Math.max(low, Math.min(high, 0));
            if (v <= zero) return wheelBetween(colours[0], colours[1], zero > low ? (v - low) / (zero - low) : 1);
            return wheelBetween(colours[1], colours[2], high > zero ? (v - zero) / (high - zero) : 1);
        }
    }

    /** The car's units, as lightvibesd last read them: "km" or "mi" (miles until it has). */
    private static boolean carUnitsKm() {
        return "km".equals(readFirstLine(new File("/data/hmgapi/public/light-vibes/units")));
    }

    private static int speedValue(Map<String, String> m, String key, int def) {
        return intOf(m.get(key), def);
    }

    private static boolean pale(int i) {
        return Math.min(WHEEL_R[i], Math.min(WHEEL_G[i], WHEEL_B[i])) > 150;
    }

    /** The same walk round the wheel as wheel_walk() in lightvibesd.c; x is 0 at the low value, 1 at the high. */
    private static int wheelBetween(int a, int b, double x) {
        a = Math.max(0, Math.min(WHEEL_R.length - 1, a));
        b = Math.max(0, Math.min(WHEEL_R.length - 1, b));
        if (a == b) return a;
        if (a >= 64 || b >= 64) {
            double ha = wheelHue(a), hb = wheelHue(b);
            double dh = hb - ha;
            if (dh > 180.0) dh -= 360.0;
            if (dh < -180.0) dh += 360.0;
            double span = Math.abs(dh);
            int[] midIdx = new int[WHEEL_R.length];
            double[] midOff = new double[WHEEL_R.length];
            int mn = 0;
            for (int idx = 0; idx < WHEEL_R.length; idx++) {
                if (idx == a || idx == b || !vividWheel(idx)) continue;
                double h = wheelHue(idx);
                double off = dh >= 0.0 ? ((h - ha) % 360.0 + 360.0) % 360.0 : ((ha - h) % 360.0 + 360.0) % 360.0;
                if (off <= 4.0 || off >= span - 4.0) continue;
                int pos = mn++;
                while (pos > 0 && midOff[pos - 1] > off) {
                    midOff[pos] = midOff[pos - 1];
                    midIdx[pos] = midIdx[pos - 1];
                    pos--;
                }
                midOff[pos] = off;
                midIdx[pos] = idx;
            }
            int[] path = new int[mn + 2];
            int n = 0;
            path[n++] = a;
            double prevOff = -99.0;
            for (int i = 0; i < mn; i++) {
                if (midOff[i] - prevOff < 2.5) continue;
                path[n++] = midIdx[i];
                prevOff = midOff[i];
            }
            path[n++] = b;
            return path[(int) Math.round(x * (n - 1))];
        }
        int d = (b - a + 64) % 64;
        if (d > 32) d -= 64;
        int steps = Math.abs(d), dir = d < 0 ? -1 : 1, n = 0;
        int[] path = new int[steps + 1];
        path[n++] = a;
        for (int i = 1; i <= steps; i++) {
            int j = (a + dir * i + 64) % 64;
            if (j == b || !pale(j)) path[n++] = j;
        }
        return path[(int) Math.round(x * (n - 1))];
    }

    private int[] gaugeColours(Gauge g) {
        String[] keys = g.colourKeys();
        int[] defs = g.colourDefaults(), colours = new int[keys.length];
        for (int i = 0; i < keys.length; i++) colours[i] = colorIndexOf(cfg.get(keys[i]), defs[i], 0);
        return colours;
    }

    /** Color tab: the colour at each end (and Power's zero), the palette, and the fade between them. */
    private void addGaugeColour(final Gauge g) {
        final String[] keys = g.colourKeys();
        final int[] colours = gaugeColours(g);
        if (speedEnd >= keys.length) speedEnd = 0;
        LinearLayout ends = new LinearLayout(this);
        ends.setBaselineAligned(false);
        for (int i = 0; i < keys.length; i++) {
            final int end = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setBackgroundDrawable(rounded(CARD, i == speedEnd ? TEXT : CARD, 3, 8));
            cell.setPadding(dp(4), dp(6), dp(4), dp(6));          // the same as Spectrum's band buttons
            View swatch = new View(this);
            swatch.setBackgroundDrawable(rounded(wheelColour(colours[i]), 0, 0, 6));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26));
            sp.leftMargin = sp.rightMargin = dp(6);
            sp.bottomMargin = dp(3);
            cell.addView(swatch, sp);
            TextView name = text(g.colourNames[i], 16, TEXT);
            name.setGravity(Gravity.CENTER);
            cell.addView(name);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    speedEnd = end;
                    refreshEditor();
                }
            });
            ends.addView(cell, weighted(0, 0, 1, i == 0 ? 0 : 8));
        }
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ep.topMargin = dp(10);
        editor.addView(ends, ep);

        addPalette(colours[speedEnd], new ColourPick() {
            @Override public void pick(int idx) {
                set(keys[speedEnd], String.valueOf(idx));
                refreshEditor();
            }
        }, 26, 8);

        editor.addView(fadeStrip(g, colours));
    }

    /** What the fade will look like over the range. */
    private LinearLayout fadeStrip(Gauge g, int[] colours) {
        LinearLayout fade = new LinearLayout(this);
        fade.setPadding(0, dp(6), 0, 0);
        for (int i = 0; i < 32; i++) fade.addView(new View(this), new LinearLayout.LayoutParams(0, dp(12), 1));
        drawFade(fade, g, colours, speedValue(cfg, g.lowKey, g.lowDef), speedValue(cfg, g.highKey, g.highDef));
        return fade;
    }

    /** Tuning tab: the range, with Now and Efficiency's source. (The fade is on the Color tab.) */
    private void addGaugeTuning(final Gauge g) {
        final boolean km = carUnitsKm();
        speedNow = text(g == Gauge.EFFICIENCY && "trip".equals(cfg.get("eff_source")) ? "Now: after the first " + (km ? "0.5 km" : "0.3 mi")
                : "Now: waiting", 16, ACCENT);   // the design's secondary colour
        speedNow.setGravity(Gravity.RIGHT);
        int[] values = {speedValue(cfg, g.lowKey, g.lowDef), speedValue(cfg, g.highKey, g.highDef)};
        addRange(g, values, km, null, gaugeColours(g));
    }

    private static void drawFade(LinearLayout fade, Gauge g, int[] colours, int low, int high) {
        for (int i = 0; i < fade.getChildCount(); i++) {
            fade.getChildAt(i).setBackgroundColor(wheelColour(g.colourAt(colours, low, high, i / (double) (fade.getChildCount() - 1))));
        }
    }

    /** Efficiency's source, as radio buttons under a title like the range's: this drive, or the car's own average. */
    private void addEffSource() {
        TextView title = text("Efficiency Based On", 16, MUTED);
        title.setPadding(0, dp(12), 0, dp(2));
        editor.addView(title);
        android.widget.RadioGroup group = new android.widget.RadioGroup(this);
        group.setOrientation(LinearLayout.HORIZONTAL);
        final boolean trip = "trip".equals(cfg.get("eff_source"));
        String[][] options = {{"trip", "Current Trip"}, {"average", "Car Average"}};
        for (int i = 0; i < options.length; i++) {
            final String v = options[i][0];
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            Widgets.teal(rb);
            rb.setId(1000 + i);
            rb.setText(options[i][1]);
            rb.setTextSize(18);
            rb.setTextColor(TEXT);
            rb.setChecked(v.equals(trip ? "trip" : "average"));
            rb.setPadding(rb.getPaddingLeft() + dp(6), 0, dp(24), 0);
            rb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                    if (!checked || v.equals(cfg.get("eff_source"))) return;
                    set("eff_source", v);
                    refreshEditor();
                }
            });
            group.addView(rb, new android.widget.RadioGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        }
        editor.addView(group);
    }

    /** One slider, two handles: the low and high ends of the range. */
    private void addRange(final Gauge g, int[] values, boolean km, final LinearLayout fade, final int[] colours) {
        final String unit = g.unit(km);
        // the title, then the range with Now beside it, then a full-width slider
        final String prefix = "";
        final TextView label = text("", 16, TEXT);
        final RangeSlider range = new RangeSlider(this, g.min, g.max, g.step, values[0], values[1], TRACK, ACCENT, TEXT);
        label.setText(prefix + g.show(range.low()) + " to " + g.show(range.high()) + " " + unit);
        range.setListener(new RangeSlider.Listener() {
            @Override public void moved(int low, int high) {
                label.setText(prefix + g.show(low) + " to " + g.show(high) + " " + unit);
                if (fade != null) drawFade(fade, g, colours, low, high);
            }
            @Override public void done(int low, int high) {
                cfg.put(g.highKey, String.valueOf(high));
                writeFile(new File(settingsDir, g.highKey), cfg.get(g.highKey));
                set(g.lowKey, String.valueOf(low));
            }
        });
        // Efficiency's source switch on its own line above; then one row like the brightness tab: label, slider, Now
        if (g == Gauge.EFFICIENCY) addEffSource();
        {
            TextView title = text(g.rangeTitle, 16, MUTED);
            title.setPadding(0, dp(12), 0, dp(2));
            editor.addView(title);
            LinearLayout line = new LinearLayout(this);
            line.setBaselineAligned(false);
            line.setGravity(Gravity.CENTER_VERTICAL);
            label.setTextSize(20);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            line.addView(label);
            speedNow.setGravity(Gravity.LEFT);
            speedNow.setPadding(dp(20), 0, 0, 0);
            line.addView(speedNow);
            editor.addView(line);
            editor.addView(range, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        }
    }

    /**
     * Brightness while Speed, Power or Efficiency drive the light, in one row: the level, and the car's own "Dimmed in
     * Dark" on or off (it keeps the level). 0.8.0 stored Auto as gauge_brightness=auto: that's level 10 with dim on.
     */
    private void addGaugeBrightness() {
        if ("auto".equals(cfg.get("gauge_brightness"))) {
            cfg.put("gauge_brightness", "10");
            writeFile(new File(settingsDir, "gauge_brightness"), "10");
            set("gauge_dim", "1");
        }
        int level = Math.max(1, Math.min(10, intOf(cfg.get("gauge_brightness"), 10)));
        final TextView label = text("Brightness: " + level, 16, TEXT);
        RangeSlider bar = level(1, 10, 1, level);
        bar.setListener(new RangeSlider.Listener() {
            @Override public void moved(int lo, int hi) { label.setText("Brightness: " + hi); }
            @Override public void done(int lo, int hi) { set("gauge_brightness", String.valueOf(hi)); }
        });
        Switch sw = new Switch(this);
        Widgets.teal(sw);
        sw.setChecked("1".equals(cfg.get("gauge_dim")));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) { set("gauge_dim", checked ? "1" : "0"); }
        });
        editor.addView(sliderRow(label, 130, bar, "Dimmed in Dark", sw));
    }

    /** "speed <in the shown unit> <i>", "power <kW> <i>" or "eff <in the shown unit> <i>" from lightvibesd. */
    private void showGauge(String line) {
        if (speedNow == null) return;
        String[] p = line.split(" ");
        String m = mode();
        Gauge g = "nightdrive".equals(m) ? ("speed".equals(cfg.get("nd_source")) ? Gauge.SPEED : Gauge.POWER) : Gauge.of(m);
        if (p.length < 3 || g == null) return;
        boolean km = carUnitsKm();
        try {
            if (p[1].equals("wait")) {
                speedNow.setText("Now: after the first " + (km ? "0.5 km" : "0.3 mi"));
                speedNow.setTextColor(ACCENT);
                return;
            }
            double v = Double.parseDouble(p[1]);
            String shown;
            if (p[0].equals("speed") && g == Gauge.SPEED) shown = Math.round(v) + " " + g.unit(km);
            else if (p[0].equals("power") && g == Gauge.POWER) shown = Math.round(v) + " kW" + (v < -0.5 ? " (regen)" : "");
            else if (p[0].equals("eff") && g == Gauge.EFFICIENCY) shown = (Math.round(v * 10) / 10.0) + " " + g.unit(km);   // the switch above says which
            else return;
            speedNow.setText("Now: " + shown);
            speedNow.setTextColor(ACCENT);
        } catch (NumberFormatException ignored) {
        }
    }

    private void configureMeter() {
        if (meter == null) return;
        List<Band> bands = bands();
        int[] colours = new int[bands.size()];
        String[] names = titlesOf(bands);
        for (int i = 0; i < colours.length; i++) colours[i] = bands.get(i).rgb();
        String mode = mode();
        meter.configure(mode.equals("spectrum"), beatsMatter(), usesLoudness(), colours, names,
                intOf(cfg.get("switch_margin"), 15), intOf(cfg.get("sensitivity"), 150), intOf(cfg.get("loudness_range_db"), 6));
    }

    /** The meter only reads from lightvibesd while the Response page is on screen. */
    private void syncMeter() {
        handler.removeCallbacks(tunePoll);
        if (resumed && (tab == TAB_TUNING || Gauge.of(mode()) != null)) {
            if (meterThread == null) {
                meterThread = new Thread(meterReader, "meter");
                meterThread.start();
            }
            if (tab == TAB_TUNING && Gauge.of(mode()) == null) handler.postDelayed(tunePoll, 800);
        } else {
            meterThread = null;    // meterReader sees this at its next line or timeout, and stops
        }
    }

    private final Runnable tunePoll = new Runnable() {
        @Override public void run() {
            if (!resumed || tab != TAB_TUNING || tuneButton == null) return;
            refreshTuneStatusFromDisk();
            handler.postDelayed(this, 800);
        }
    };

    private final Runnable meterReader = new Runnable() {
        @Override public void run() {
            Thread me = Thread.currentThread();
            while (meterThread == me) {
                LocalSocket s = new LocalSocket();
                try {
                    s.connect(new LocalSocketAddress("light-vibes.meter"));
                    s.setSoTimeout(1500);
                    BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
                    String line;
                    while (meterThread == me && (line = r.readLine()) != null) {
                        final String l = line;
                        handler.post(new Runnable() {     // not meter.post: it never runs while the meter isn't on screen
                            @Override public void run() {
                                if (l.startsWith("speed ") || l.startsWith("power ") || l.startsWith("eff ")) showGauge(l);
                                else if (l.startsWith("cap ")) applyTuneStatus(l.substring(4));
                                else meter.update(l);
                            }
                        });
                    }
                } catch (IOException e) {
                    // no music, or the service is restarting: try again
                } finally {
                    try { s.close(); } catch (IOException ignored) { }
                }
                try { Thread.sleep(500); } catch (InterruptedException e) { return; }
            }
        }
    };

    private void addResponse() {
        String mode = mode();
        boolean spectrum = mode.equals("spectrum");
        if (mode.equals("nightdrive")) {
            addGaugeTuning("speed".equals(cfg.get("nd_source")) ? Gauge.SPEED : Gauge.POWER);
        }
        List<View> tiles = new ArrayList<View>();
        if (spectrum) {
            tiles.add(slider("Colour smoothing", "Lower reacts faster, higher is calmer", "band_smoothing_ms", 20, 0, 600, 10, " ms"));
            tiles.add(slider("Colour stickiness", "How much louder a new band must get (marker line)", "switch_margin", 15, 0, 200, 5, "%"));
        } else if (mode.equals("synesthesia")) {
            tiles.add(slider("Pitch smoothing", "Lower tracks fast notes, higher glides smoothly", "band_smoothing_ms", 115, 20, 600, 10, " ms"));
        } else if (mode.equals("drop")) {
            tiles.add(slider("Drop trigger", "Higher enters full drop more easily", "drop_sensitivity", 100, 50, 150, 5, "%"));
        }
        tiles.add(slider("Fastest light change", "Shortest time between changes", "min_interval_ms", 50, 20, 1000, 10, " ms"));
        // only what has an effect with the other settings: loudness moves the brightness only when Average loudness is active
        if (usesLoudness()) tiles.add(slider("Brightness swing", "Lower makes brightness more dramatic", "loudness_range_db", 6, 2, 24, 1, " dB"));
        addSection("Light");
        addGrid(tiles);

        if (spectrum) {
            List<Band> bands = bands();
            if (selectedBand >= bands.size()) selectedBand = 0;
            addSection("Band sensitivity");
            boolean autoAdapt = !"0".equals(cfg.get("spectrum_dynamic"));
            addRadios(2200, new String[][] {{"1", "Auto-adapt"}, {"0", "Manual only"}}, autoAdapt ? "1" : "0", "spectrum_dynamic");
            addBandChips(bands, 10, false);
            if (!bands.isEmpty()) editor.addView(boostSlider(bands));
        }

        if (beatsMatter()) {      // Spectrum and Synesthesia only use beats for flashes
            tiles = new ArrayList<View>();
            tiles.add(slider("Beat sensitivity", "Lower catches more beats (marker line)", "sensitivity", 150, 105, 500, 5, "%"));
            tiles.add(slider("Shortest beat gap", "Ignores beats closer together", "min_beat_ms", 200, 60, 1000, 10, " ms"));
            tiles.add(slider("Beat average", "Shorter adapts to build-ups faster", "beat_avg_ms", 1500, 200, 5000, 100, " ms"));
            int glowDef = mode.equals("ember") ? 52 : mode.equals("nightdrive") ? 45 : 35;
            if (pulseLike()) tiles.add(slider("Glow", "Higher fades more slowly", "pulse_decay", glowDef, 0, 99, 1, "%"));
            else if (flashing()) tiles.add(slider("Flash length", "How long each flash holds", "flash_ms", 120, 30, 1000, 10, " ms"));
            tiles.add(beatRange());
            addSection("Beat detection");
            boolean autoBpm = !"0".equals(cfg.get("auto_bpm"));
            addRadios(2300, new String[][] {{"1", "Auto BPM (tempo sync)"}, {"0", "Manual timing"}}, autoBpm ? "1" : "0", "auto_bpm");
            addGrid(tiles);
        }

        addTuneCapture();
    }

    private void addTuneCapture() {
        addSection("Tuning capture");
        LinearLayout row = new LinearLayout(this);
        row.setBaselineAligned(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(10));
        tuneButton = button("Record 20s clip to USB", false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                File diag = new File(getFilesDir(), "diag");
                diag.mkdirs();
                tuneRequestedAt = System.currentTimeMillis();
                if (tuneActive) {
                    writeFile(new File(diag, "request"), "capture stop");
                    applyTuneStatus("stopped Stopping recording...");
                } else {
                    writeFile(new File(diag, "request"), "capture 20");
                    applyTuneStatus("wait Starting 20s USB recording...");
                }
            }
        });
        tuneButton.setPadding(dp(16), 0, dp(16), 0);
        row.addView(tuneButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        tuneStatus = text("Saves 20s loopback WAV + 10 ms frame CSV + settings to USB", 14, MUTED);
        tuneStatus.setPadding(dp(14), 0, 0, 0);
        row.addView(tuneStatus, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        editor.addView(row);
        refreshTuneStatusFromDisk();
        handler.removeCallbacks(tunePoll);
        handler.postDelayed(tunePoll, 800);
    }

    private void refreshTuneStatusFromDisk() {
        File f = new File("/data/hmgapi/public/light-vibes/tune_status");
        if (!f.exists()) return;
        String line = readFirstLine(f);
        if (line == null || line.isEmpty()) return;
        boolean fresh = (System.currentTimeMillis() - f.lastModified() < 120000L)
                || (tuneRequestedAt > 0 && f.lastModified() >= tuneRequestedAt - 2000L)
                || line.startsWith("rec ") || line.startsWith("wait ");
        if (fresh) applyTuneStatus(line);
    }

    private void applyTuneStatus(String s) {
        if (tuneButton == null || tuneStatus == null || s == null) return;
        int sp = s.indexOf(' ');
        String state = sp > 0 ? s.substring(0, sp) : s;
        String msg = sp > 0 ? s.substring(sp + 1) : s;
        boolean activeNow = "rec".equals(state) || "wait".equals(state);
        tuneActive = activeNow;
        tuneButton.setText(activeNow ? ("rec".equals(state) ? "Stop recording" : "Cancel") : "Record 20s clip to USB");
        tuneButton.setTextColor(activeNow ? ON_TEXT : TEXT);
        tuneButton.setBackgroundDrawable(rounded(activeNow ? CARD_ON : CARD, 0, 0, 8));
        tuneButton.setPadding(dp(16), 0, dp(16), 0);
        tuneStatus.setText(msg);
        tuneStatus.setTextColor("error".equals(state) ? TEXT : (activeNow || "done".equals(state)) ? ACCENT : MUTED);
    }

    private void addGrid(List<View> tiles) {
        for (int i = 0; i < tiles.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.addView(tiles.get(i), weighted(0, 0, 1, 0));
            if (i + 1 < tiles.size()) row.addView(tiles.get(i + 1), weighted(0, 0, 1, 16));
            else row.addView(new View(this), weighted(0, 0, 1, 16));
            editor.addView(row);
        }
    }

    /** One settings row, as on the Speed/Power/Efficiency brightness tab: a label, the slider, and an optional switch. */
    private LinearLayout sliderRow(TextView label, int labelDp, View slider, String switchLabel, Switch sw) {
        LinearLayout row = new LinearLayout(this);
        row.setBaselineAligned(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);
        row.addView(label, new LinearLayout.LayoutParams(dp(labelDp), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(slider, new LinearLayout.LayoutParams(0, dp(42), 1));
        if (sw != null) {
            TextView t = text(switchLabel, 16, TEXT);
            t.setPadding(dp(16), 0, 0, 0);
            row.addView(t);
            sw.setTextOn("On");
            sw.setTextOff("Off");
            LinearLayout.LayoutParams swp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            swp.leftMargin = dp(10);
            row.addView(sw, swp);
        }
        return row;
    }

    /** A one-handle slider in the app's style, for a plain value. */
    private RangeSlider level(int min, int max, int step, int value) {
        return RangeSlider.level(this, min, max, step, value, TRACK, ACCENT, TEXT);
    }

    /** A slider in the Speed range's style: a small grey title, the value in bold with a hint beside it, the slider under. */
    private View tile(String title, TextView value, String hint, View bar) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(title, 15, MUTED);
        t.setPadding(0, dp(8), 0, 0);
        col.addView(t);
        LinearLayout line = new LinearLayout(this);
        line.setBaselineAligned(false);
        line.setGravity(Gravity.CENTER_VERTICAL);
        value.setTextSize(20);
        value.setTextColor(TEXT);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        line.addView(value);
        if (hint != null) {
            TextView h = text(hint, 14, MUTED);
            h.setPadding(dp(14), 0, 0, 0);
            h.setSingleLine(true);
            h.setEllipsize(android.text.TextUtils.TruncateAt.END);
            line.addView(h, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        col.addView(line);
        col.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        return col;
    }

    private View slider(final String title, String hint, final String key, int def, final int lo, int hi, final int step, final String unit) {
        int v0 = Math.max(lo, Math.min(hi, intOf(cfg.get(key), def)));
        final TextView value = text(v0 + unit, 20, TEXT);
        RangeSlider bar = level(lo, hi, step, v0);
        bar.setListener(new RangeSlider.Listener() {
            @Override public void moved(int a, int v) { value.setText(v + unit); }
            @Override public void done(int a, int v) { set(key, String.valueOf(v)); }
        });
        return tile(title, value, hint, bar);
    }

    /** The beat range as one two-handle slider, like Speed's range. */
    private View beatRange() {
        int lo = Math.max(20, Math.min(480, intOf(cfg.get("beat_low_hz"), 40)));
        int hi = Math.max(lo + 20, Math.min(500, intOf(cfg.get("beat_high_hz"), 180)));
        final TextView value = text(lo + " to " + hi + " Hz", 20, TEXT);
        RangeSlider bar = new RangeSlider(this, 20, 500, 5, lo, hi, TRACK, ACCENT, TEXT).gap(4);
        bar.setListener(new RangeSlider.Listener() {
            @Override public void moved(int a, int b) { value.setText(a + " to " + b + " Hz"); }
            @Override public void done(int a, int b) {
                cfg.put("beat_high_hz", String.valueOf(b));
                writeFile(new File(settingsDir, "beat_high_hz"), cfg.get("beat_high_hz"));
                set("beat_low_hz", String.valueOf(a));
            }
        });
        return tile("Beat range", value, "Kick and bass to 180 Hz, snare to 400", bar);
    }

    /** A band's boost, ×0.1 to ×100 on a log scale: it multiplies that band's energy before the bands compete. */
    private View boostSlider(final List<Band> bands) {
        final Band band = bands.get(selectedBand);
        final String title = titlesOf(bands)[selectedBand];
        double boost = 1;
        try { if (band.boost != null) boost = Double.parseDouble(band.boost); } catch (NumberFormatException ignored) { }
        final TextView value = text("×" + boostText(boost), 20, TEXT);
        RangeSlider bar = level(0, 60, 1, (int) Math.round(20 * Math.log10(Math.max(0.1, Math.min(100, boost)) / 0.1)));
        bar.setListener(new RangeSlider.Listener() {
            @Override public void moved(int a, int p) { value.setText("×" + boostText(boostAt(p))); }
            @Override public void done(int a, int p) {
                band.boost = boostText(boostAt(p));
                saveBands(bands);
            }
        });
        return tile(title + " sensitivity", value, "Raise a band that never lights up, lower one that wins too often", bar);
    }

    private static double boostAt(int p) {
        return 0.1 * Math.pow(10, p / 20.0);
    }

    private static String boostText(double v) {
        if (v >= 10) return String.valueOf(Math.round(v));
        return String.valueOf(Math.round(v * 10) / 10.0);
    }


    // ---------- dialogs ----------

    /** Load a preset (tapping the active one again undoes its edits). */
    private void switchTo(String id) {
        applyPreset(id);
        closeReport();
        refreshPresets();
        refreshEditor();
    }

    // ---------- pop-ups: the system's own dialogs (Android's AlertDialog, in the car's light or dark Holo style) ----------

    /** The current preset has unsaved edits and another one was tapped: keep them (Save, Save as new) or drop them. */
    private void askUnsaved(final String next) {
        new AlertDialog.Builder(this)
                .setTitle("Save changes to " + titleOf(active) + "?")
                .setMessage("Your changes will be lost if you don't save them.")
                .setNegativeButton("Discard", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { switchTo(next); }
                })
                .setNeutralButton("Save as new", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        askSave(new Runnable() { @Override public void run() { switchTo(next); } });
                    }
                })
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { saveOverActive(); switchTo(next); }
                })
                .show();
    }

    private void askSave() {
        askSave(null);
    }

    /** Save as: then runs after (if any) once saved. */
    private void askSave(final Runnable after) {
        final EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT);
        name.setText(active.startsWith("c:") ? active.substring(2) : freeName());
        name.selectAll();
        final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Save preset as")
                .setView(name)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String n = cleanName(name.getText().toString());
                        savePreset(n.isEmpty() ? freeName() : n);     // same name as a custom preset: saves over it, as asked
                        refreshPresets();
                        if (after != null) after.run();
                    }
                })
                .create();
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
    }

    /**
     * Any name the owner types. A custom preset is a file of its own (files/presets/<name>.conf, id "c:<name>"), apart
     * from the built-ins, so it can share a built-in's name. Only what can't be in a file name goes: "/", control
     * characters, and a leading dot (hidden files).
     */
    static String cleanName(String raw) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '/' && c != '\\' && !Character.isISOControl(c)) b.append(c);
        }
        String n = b.toString().trim();
        while (n.startsWith(".")) n = n.substring(1).trim();
        while (n.endsWith("*")) n = n.substring(0, n.length() - 1).trim();   // "*" at the end marks an edited preset in files/active
        if (n.length() > 40) n = n.substring(0, 40).trim();
        return n;
    }

    /** "Power" if it's free, otherwise "Power 2", "Power 3"... */
    private String unusedName(String n) {
        if (!new File(presetsDir, n + ".conf").exists()) return n;
        for (int i = 2; ; i++) if (!new File(presetsDir, n + " " + i + ".conf").exists()) return n + " " + i;
    }

    private String freeName() {
        for (int i = 1; ; i++) {
            String n = "My preset " + i;
            if (!new File(presetsDir, n + ".conf").exists()) return n;
        }
    }

    /** New presets start from Lively as it shipped: its timing suits most music, and all of it can be changed. */
    private void askNew() {
        final EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT);
        name.setText(freeName());
        name.selectAll();
        final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("New preset")
                .setView(name)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String n = cleanName(name.getText().toString());
                        n = n.isEmpty() ? freeName() : unusedName(n);
                        cfg = readShipped("lively");
                        selectedBand = 0;
                        writeAllSettings();
                        savePreset(n);
                        refreshPresets();
                        refreshEditor();
                    }
                })
                .create();
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
    }

    private void askDelete() {
        final boolean custom = active.startsWith("c:");
        final String id = active;
        List<String> others = presetIds();
        others.remove(id);
        if (others.isEmpty()) return;
        final String next = others.get(0);
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("Delete " + titleOf(id) + "?")
                .setMessage("The lights switch to " + titleOf(next) + "."
                        + (custom ? "" : " Built-in presets can be restored later."))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (custom) {
                            new File(presetsDir, id.substring(2) + ".conf").delete();
                        } else {
                            overrideOf(id).delete();
                            writeFile(new File(hiddenDir, id.substring(2)), "1");
                        }
                        applyPreset(next);
                        refreshPresets();
                        refreshEditor();
                    }
                });
        if (!custom && overrideOf(id).exists()) {
            b.setNeutralButton("Reset instead", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    overrideOf(id).delete();
                    applyPreset(id);
                    refreshPresets();
                    refreshEditor();
                }
            });
        }
        b.show();
    }
}
