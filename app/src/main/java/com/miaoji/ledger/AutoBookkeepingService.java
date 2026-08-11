package com.miaoji.ledger;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public final class AutoBookkeepingService extends AccessibilityService {
    private static final String PREFS = "miaoji_settings";
    private static final int BLUE = Color.rgb(174, 202, 247);
    private static final int INK = Color.rgb(0, 13, 8);
    private static final String[] PURPOSES = {"餐饮", "购物", "交通", "娱乐", "居住", "医疗", "学习", "其他"};

    private WindowManager windowManager;
    private View overlayView;
    private String lastFingerprint = "";
    private long lastPromptAt;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!preferences.getBoolean("auto_enabled", true) || overlayView != null || event == null) return;

        CharSequence packageValue = event.getPackageName();
        String packageName = packageValue == null ? "" : packageValue.toString();
        if (!"com.tencent.mm".equals(packageName) && !"com.eg.android.AlipayGphone".equals(packageName)) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        StringBuilder visibleText = new StringBuilder();
        appendVisibleText(root, visibleText, 0);
        root.recycle();

        String content = visibleText.toString();
        if (!containsSuccessSignal(content)) return;
        String sourceTitle = "com.tencent.mm".equals(packageName) ? "微信支付" : "支付宝";
        LedgerEntry entry = NotificationTransactionParser.parse(
                packageName, sourceTitle, content, System.currentTimeMillis());
        if (entry == null) return;
        entry.note = "";

        long now = System.currentTimeMillis();
        if (entry.fingerprint.equals(lastFingerprint) && now - lastPromptAt < 120_000L) return;
        lastFingerprint = entry.fingerprint;
        lastPromptAt = now;
        showPurposePrompt(entry);
    }

    @Override public void onInterrupt() {
        dismissPrompt();
    }

    @Override public void onDestroy() {
        dismissPrompt();
        super.onDestroy();
    }

    private boolean containsSuccessSignal(String text) {
        return text.contains("支付成功") || text.contains("付款成功") || text.contains("交易成功")
                || text.contains("已支付") || text.contains("收款成功");
    }

    private void appendVisibleText(AccessibilityNodeInfo node, StringBuilder out, int depth) {
        if (node == null || depth > 40 || out.length() > 12_000) return;
        CharSequence text = node.getText();
        if (text != null && text.length() > 0) out.append(text).append(' ');
        CharSequence description = node.getContentDescription();
        if (description != null && description.length() > 0 && (text == null || !description.toString().contentEquals(text))) {
            out.append(description).append(' ');
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) continue;
            appendVisibleText(child, out, depth + 1);
            child.recycle();
        }
    }

    private void showPurposePrompt(LedgerEntry entry) {
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) return;

        FrameLayout shade = new FrameLayout(this);
        shade.setBackgroundColor(0x66000000);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(22), dp(18), dp(22), dp(22));
        sheet.setBackground(round(Color.WHITE, 28));

        LinearLayout heading = row();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleCopy = column();
        titleCopy.addView(text("识别到一笔付款", 18, INK, true));
        titleCopy.addView(text(entry.source + " · 请选择用途", 12, 0xFF999999, false));
        heading.addView(titleCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView close = text("×", 28, 0xFF777777, false);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dismissPrompt());
        heading.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        sheet.addView(heading);

        TextView amount = text(String.format(Locale.CHINA, "¥%,.2f", entry.amountCents / 100d), 38, INK, true);
        amount.setGravity(Gravity.CENTER);
        amount.setPadding(0, dp(14), 0, dp(4));
        sheet.addView(amount);
        TextView merchant = text(entry.merchant, 13, 0xFF999999, false);
        merchant.setGravity(Gravity.CENTER);
        sheet.addView(merchant);

        TextView purposeTitle = text("这笔钱用在了哪里？", 15, INK, true);
        purposeTitle.setPadding(0, dp(20), 0, dp(10));
        sheet.addView(purposeTitle);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setRowCount(2);
        String[] selectedPurpose = {null};
        TextView[] purposeViews = new TextView[PURPOSES.length];
        Button save = new Button(this);
        save.setText("完成记账");
        save.setTextSize(16);
        save.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        save.setTextColor(Color.WHITE);
        save.setAllCaps(false);
        save.setEnabled(false);
        save.setAlpha(0.35f);
        save.setBackground(round(INK, 20));

        for (int index = 0; index < PURPOSES.length; index++) {
            String purpose = PURPOSES[index];
            TextView choice = text(purposeIcon(purpose) + "\n" + purpose, 13, INK, true);
            choice.setGravity(Gravity.CENTER);
            choice.setBackground(round(0xFFF2F2F2, 22));
            final int selectedIndex = index;
            choice.setOnClickListener(v -> {
                selectedPurpose[0] = purpose;
                for (int i = 0; i < purposeViews.length; i++) {
                    purposeViews[i].setBackground(round(i == selectedIndex ? BLUE : 0xFFF2F2F2, 22));
                }
                save.setEnabled(true);
                save.setAlpha(1f);
            });
            purposeViews[index] = choice;
            GridLayout.LayoutParams choiceParams = new GridLayout.LayoutParams();
            choiceParams.width = 0;
            choiceParams.height = dp(76);
            choiceParams.columnSpec = GridLayout.spec(index % 4, 1f);
            choiceParams.rowSpec = GridLayout.spec(index / 4);
            choiceParams.setMargins(dp(4), dp(4), dp(4), dp(4));
            grid.addView(choice, choiceParams);
        }
        sheet.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(168)));

        save.setOnClickListener(v -> {
            if (selectedPurpose[0] == null) return;
            entry.category = selectedPurpose[0];
            try (LedgerDb ledgerDb = new LedgerDb(getApplicationContext())) {
                long id = ledgerDb.insert(entry);
                if (id != -1) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putLong("last_auto_at", System.currentTimeMillis())
                            .putString("last_auto_merchant", entry.merchant)
                            .apply();
                    Toast.makeText(this, "已记账 · " + entry.category, Toast.LENGTH_SHORT).show();
                }
            }
            dismissPrompt();
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        saveParams.setMargins(0, dp(12), 0, 0);
        sheet.addView(save, saveParams);

        FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        sheetParams.setMargins(dp(10), 0, dp(10), dp(10));
        shade.addView(sheet, sheetParams);
        overlayView = shade;

        WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        windowParams.gravity = Gravity.TOP | Gravity.START;
        try {
            windowManager.addView(shade, windowParams);
        } catch (RuntimeException error) {
            overlayView = null;
        }
    }

    private void dismissPrompt() {
        if (windowManager != null && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {
                // The system may already have removed the accessibility overlay.
            }
        }
        overlayView = null;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String purposeIcon(String purpose) {
        switch (purpose) {
            case "餐饮": return "餐";
            case "购物": return "购";
            case "交通": return "行";
            case "娱乐": return "娱";
            case "居住": return "居";
            case "医疗": return "医";
            case "学习": return "学";
            default: return "其";
        }
    }
}
