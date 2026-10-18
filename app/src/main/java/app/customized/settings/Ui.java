package app.customized.settings;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * 所有页面共用的界面小工具。
 * 重点：按遥控器方向键移动焦点时，被选中的控件会变成【亮琥珀色底 + 白色粗边框 + 深色字】，
 * 和普通状态（深灰底）区别很大，一眼就能看出选中了哪个。
 */
final class Ui {

    static final int COLOR_BG = Color.parseColor("#14171C");
    static final int COLOR_TEXT = Color.WHITE;
    static final int COLOR_NOTE = Color.parseColor("#B8C0CC");
    static final int COLOR_OK = Color.parseColor("#9FE8A8");

    private static final int FILL_NORMAL = Color.parseColor("#2A3140");
    private static final int STROKE_NORMAL = Color.parseColor("#4A5264");
    private static final int FILL_DISABLED = Color.parseColor("#1E232C");
    private static final int AMBER = Color.parseColor("#FFB300");
    private static final int AMBER_PRESSED = Color.parseColor("#FF8F00");
    private static final int TEXT_ON_AMBER = Color.parseColor("#14171C");
    private static final int TEXT_DISABLED = Color.parseColor("#6B7280");

    /** 表单行里“名称”列的宽度（dp），所有输入框因此左对齐成一列。 */
    static final int LABEL_WIDTH_DP = 230;

    private Ui() {}

    // ------------------------------------------------------------------
    // 页面骨架
    // ------------------------------------------------------------------

    static final class Page {
        final LinearLayout root;
        private final ScrollView scroll;
        private final Activity activity;

        Page(Activity activity) {
            this.activity = activity;
            scroll = new ScrollView(activity);
            scroll.setBackgroundColor(COLOR_BG);
            scroll.setFillViewport(true);
            root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(activity, 48), dp(activity, 32), dp(activity, 48), dp(activity, 32));
            scroll.addView(root);
        }

        void show() {
            activity.setContentView(scroll);
        }
    }

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------
    // 文字
    // ------------------------------------------------------------------

    static TextView title(Context c, String s) {
        return text(c, s, 30, true, COLOR_TEXT);
    }

    static TextView section(Context c, String s) {
        return text(c, s, 20, true, COLOR_TEXT);
    }

    static TextView note(Context c, String s) {
        return text(c, s, 16, false, COLOR_NOTE);
    }

    static TextView result(Context c, String s) {
        return text(c, s, 16, false, COLOR_OK);
    }

    static TextView text(Context c, String s, int sp, boolean bold, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setPadding(0, dp(c, 10), 0, dp(c, 4));
        return t;
    }

    // ------------------------------------------------------------------
    // 控件
    // ------------------------------------------------------------------

    static Button button(Context c, String s, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(s);
        b.setTextSize(18);
        b.setAllCaps(false);
        b.setMinHeight(dp(c, 52));
        b.setMinimumHeight(dp(c, 52));
        b.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
        b.setBackground(focusBackground(c, FILL_NORMAL, STROKE_NORMAL, 2));
        b.setTextColor(textColors());
        b.setOnClickListener(l);
        b.setFocusable(true);
        return b;
    }

    /** 一行里并排放几个按钮，宽度完全相同。 */
    static LinearLayout buttonRow(Context c, Button... buttons) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < buttons.length; i++) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            p.leftMargin = i == 0 ? 0 : dp(c, 12);
            row.addView(buttons[i], p);
        }
        return row;
    }

    static EditText edit(Context c, String initial, boolean numericDots) {
        EditText e = new EditText(c);
        e.setText(initial);
        e.setTextSize(20);
        e.setTextColor(COLOR_TEXT);
        e.setSingleLine(true);
        e.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        e.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        e.setBackground(inputBackground(c));
        if (numericDots) {
            e.setInputType(InputType.TYPE_CLASS_NUMBER);
            e.setKeyListener(DigitsKeyListener.getInstance("0123456789."));
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        return e;
    }

    static EditText numberEdit(Context c, String initial) {
        EditText e = edit(c, initial, false);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    static RadioButton radio(Context c, String s) {
        RadioButton r = new RadioButton(c);
        r.setId(View.generateViewId());
        r.setText(s);
        r.setTextSize(20);
        r.setTextColor(textColors());
        r.setPadding(dp(c, 12), dp(c, 10), dp(c, 24), dp(c, 10));
        r.setBackground(focusBackground(c, Color.TRANSPARENT, Color.TRANSPARENT, 0));
        return r;
    }

    static Switch toggle(Context c, String s) {
        Switch sw = new Switch(c);
        sw.setText(s);
        sw.setTextSize(20);
        sw.setTextColor(textColors());
        sw.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        sw.setBackground(focusBackground(c, Color.TRANSPARENT, STROKE_NORMAL, 2));
        return sw;
    }

    // ------------------------------------------------------------------
    // 表单行： [名称]  [输入框]  说明
    // ------------------------------------------------------------------

    /**
     * 名称、输入框、说明放在同一行；所有行的名称列宽度相同，所以输入框自然对齐成一列。
     *
     * @param note 括号里的说明，放在输入框后面；可为 null
     */
    static LinearLayout formRow(Context c, String label, View input, int inputWidthDp, String note) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView l = text(c, label, 18, false, COLOR_NOTE);
        row.addView(l, new LinearLayout.LayoutParams(dp(c, LABEL_WIDTH_DP),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        row.addView(input, new LinearLayout.LayoutParams(dp(c, inputWidthDp),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        if (note != null) {
            TextView n = text(c, note, 15, false, COLOR_NOTE);
            n.setPadding(dp(c, 16), 0, 0, 0);
            row.addView(n, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(c, 6);
        row.setLayoutParams(p);
        return row;
    }

    static LinearLayout.LayoutParams matchWrap(Context c, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(c, topDp);
        return p;
    }

    // ------------------------------------------------------------------
    // 焦点样式
    // ------------------------------------------------------------------

    private static GradientDrawable box(Context c, int fill, int stroke, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, 8));
        if (strokeDp > 0) d.setStroke(dp(c, strokeDp), stroke);
        return d;
    }

    /** 普通：fill；按下：深琥珀；获得焦点：亮琥珀 + 白色粗边框。 */
    private static StateListDrawable focusBackground(Context c, int fill, int stroke, int strokeDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, box(c, AMBER_PRESSED, Color.WHITE, 4));
        s.addState(new int[]{android.R.attr.state_focused}, box(c, AMBER, Color.WHITE, 4));
        s.addState(new int[]{-android.R.attr.state_enabled}, box(c, FILL_DISABLED, STROKE_NORMAL, 1));
        s.addState(new int[]{}, box(c, fill, stroke, strokeDp));
        return s;
    }

    /** 输入框：普通时深底灰边；获得焦点时仍是深底（文字保持白色），但边框变成粗的亮琥珀色。 */
    private static StateListDrawable inputBackground(Context c) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused},
                box(c, Color.parseColor("#2B3342"), AMBER, 4));
        s.addState(new int[]{-android.R.attr.state_enabled}, box(c, FILL_DISABLED, STROKE_NORMAL, 1));
        s.addState(new int[]{}, box(c, FILL_DISABLED, STROKE_NORMAL, 2));
        return s;
    }

    private static ColorStateList textColors() {
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_pressed},
                        new int[]{android.R.attr.state_focused},
                        new int[]{-android.R.attr.state_enabled},
                        new int[]{}},
                new int[]{TEXT_ON_AMBER, TEXT_ON_AMBER, TEXT_DISABLED, COLOR_TEXT});
    }
}
