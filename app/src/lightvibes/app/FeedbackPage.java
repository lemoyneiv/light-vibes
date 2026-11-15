package lightvibes.app;

import static lightvibes.app.Ui.*;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Feedback: ideas and requests, as a GitHub issue labelled "enhancement" (the feedback.yml form), styled in Material 3.
 */
final class FeedbackPage extends LinearLayout {
    private static final String ISSUE = "https://github.com/dibble9012/light-vibes/issues/new?template=feedback.yml&labels=enhancement";

    FeedbackPage(Context c, String mode, final Runnable close) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundDrawable(Widgets.shape(CARD, RULE, dp(1), dp(20)));
        setPadding(dp(20), dp(18), dp(20), dp(18));

        LinearLayout left = new LinearLayout(c);
        left.setOrientation(VERTICAL);
        TextView h = text("Send feedback", 21, TEXT);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(0, 0, 0, dp(8));
        left.addView(h);
        left.addView(text("Have an idea or something you'd like changed? Scan the QR code with your phone to open a "
                + "suggestion on GitHub, describe it, and submit it.", 16, TEXT));
        TextView note = text("Nothing is sent from the car. The code only pre-fills the app version, head unit and current mode.", 14, MUTED);
        note.setPadding(0, dp(10), 0, 0);
        left.addView(note);

        TextView done = text("Back to settings", 15, ON_PRIMARY_CONT);
        done.setTypeface(Typeface.DEFAULT_BOLD);
        done.setGravity(Gravity.CENTER);
        done.setPadding(dp(22), 0, dp(22), 0);
        done.setBackgroundDrawable(Widgets.shape(PRIMARY_CONT, 0, 0, dp(22)));
        done.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { close.run(); }
        });
        LayoutParams dpBtn = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        dpBtn.topMargin = dp(18);
        left.addView(done, dpBtn);
        addView(left, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout qrCard = new LinearLayout(c);
        qrCard.setPadding(dp(10), dp(10), dp(10), dp(10));
        qrCard.setBackgroundDrawable(Widgets.shape(0xFFFFFFFF, RULE, dp(1), dp(16)));
        qrCard.addView(new QrView(c, link(c, mode)), new LayoutParams(dp(290), dp(290)));
        LayoutParams qp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qp.leftMargin = dp(20);
        addView(qrCard, qp);
    }

    static String link(Context c, String mode) {
        String version = "?";
        try { version = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Exception ignored) { }
        String about = "LV " + version + "\n" + prop("ro.build.product") + "\nmode " + mode;
        try {
            return ISSUE + "&about=" + URLEncoder.encode(about, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return ISSUE;
        }
    }

    private static String prop(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String v = (String) sp.getMethod("get", String.class).invoke(null, key);
            return v == null || v.isEmpty() ? "?" : v;
        } catch (Exception e) {
            return "?";
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, float sp, int colour) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(colour);
        return t;
    }
}
