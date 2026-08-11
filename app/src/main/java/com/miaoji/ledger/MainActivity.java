package com.miaoji.ledger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final int EXPORT_CSV = 71;
    private static final String[] CATEGORIES = {"餐饮", "交通", "购物", "居住", "娱乐", "医疗", "学习", "其他", "收入"};
    private static final int BRAND = Color.rgb(90, 222, 198);
    private static final int SUB = Color.rgb(220, 252, 246);
    private static final int INK = Color.rgb(0, 13, 8);
    private static final int MUTED = Color.rgb(179, 179, 179);
    private static final int PAGE = Color.rgb(244, 244, 244);
    private static final int GREEN = Color.rgb(22, 163, 74);

    private LedgerDb db;
    private FrameLayout content;
    private LinearLayout navigation;
    private int currentPage = 0;
    private String selectedCategory = "全部";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(PAGE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        db = new LedgerDb(this);
        setContentView(buildShell());
        showPage(0);
    }

    @Override protected void onResume() {
        super.onResume();
        if (content != null) showPage(currentPage);
    }

    @Override protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    private View buildShell() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(PAGE);

        content = new FrameLayout(this);
        shell.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        shell.addView(buildNavigation(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));
        return shell;
    }

    private View buildNavigation() {
        LinearLayout nav = new LinearLayout(this);
        navigation = nav;
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(4), dp(8), dp(8));
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(dp(12));
        nav.addView(navItem("首页", 0));
        nav.addView(navItem("明细", 1));

        TextView add = text("＋", 30, Color.WHITE, true);
        add.setGravity(Gravity.CENTER);
        add.setBackground(round(INK, 28));
        add.setElevation(dp(5));
        add.setOnClickListener(v -> showAddDialog());
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(dp(56), dp(56));
        addLp.setMargins(dp(8), 0, dp(8), dp(12));
        nav.addView(add, addLp);

        nav.addView(navItem("统计", 2));
        nav.addView(navItem("我的", 3));
        return nav;
    }

    private View navItem(String label, int page) {
        String icon = page == 0 ? "⌂" : page == 1 ? "≡" : page == 2 ? "▥" : "◉";
        TextView item = text(icon + "\n" + label, 11, page == currentPage ? INK : MUTED, true);
        item.setGravity(Gravity.CENTER);
        item.setOnClickListener(v -> showPage(page));
        item.setTag(page);
        item.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return item;
    }

    private void showPage(int page) {
        currentPage = page;
        if (navigation != null) {
            for (int i = 0; i < navigation.getChildCount(); i++) {
                View child = navigation.getChildAt(i);
                if (child instanceof TextView && child.getTag() instanceof Integer) {
                    child.setAlpha(((Integer) child.getTag()) == page ? 1f : 0.45f);
                }
            }
        }
        content.removeAllViews();
        if (page == 0) content.addView(buildHome());
        else if (page == 1) content.addView(buildEntries());
        else if (page == 2) content.addView(buildStats());
        else content.addView(buildSettings());
    }

    private View buildHome() {
        ScrollView scroll = scroll();
        LinearLayout page = page();
        scroll.addView(page);

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(new Space(this), new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView bookTitle = text("日常账本  ▾", 17, INK, true);
        bookTitle.setGravity(Gravity.CENTER);
        header.addView(bookTitle, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView search = text("⌕", 25, INK, true);
        search.setGravity(Gravity.CENTER);
        search.setOnClickListener(v -> showPage(1));
        header.addView(search, new LinearLayout.LayoutParams(dp(40), dp(40)));
        page.addView(header);

        long expense = db.monthTotal(false);
        long income = db.monthTotal(true);
        LinearLayout summary = column();
        summary.setPadding(dp(22), dp(20), dp(22), dp(20));
        GradientDrawable summaryBackground = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(90, 222, 198), Color.rgb(112, 229, 208)});
        summaryBackground.setCornerRadius(dp(18));
        summary.setBackground(summaryBackground);
        summary.addView(text("本月支出", 14, INK, true));
        TextView balance = text(formatMoney(expense, false), 35, INK, true);
        LinearLayout.LayoutParams balanceLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        balanceLp.setMargins(0, dp(5), 0, dp(18));
        summary.addView(balance, balanceLp);
        LinearLayout mini = row();
        long daily = expense / Math.max(1, Integer.parseInt(new SimpleDateFormat("d", Locale.ROOT).format(new Date())));
        mini.addView(summaryMetric("本月收入", formatMoney(income, false)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        mini.addView(summaryMetric("日均支出", formatMoney(daily, false)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        summary.addView(mini);
        LinearLayout.LayoutParams summaryLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryLp.setMargins(0, dp(10), 0, dp(10));
        page.addView(summary, summaryLp);

        page.addView(buildAutoBanner());

        LinearLayout sectionTitle = row();
        sectionTitle.setGravity(Gravity.CENTER_VERTICAL);
        sectionTitle.addView(text("今日账单", 14, INK, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView all = text("查看全部", 13, BRAND, true);
        all.setOnClickListener(v -> showPage(1));
        sectionTitle.addView(all);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMargins(0, dp(10), 0, dp(10));
        page.addView(sectionTitle, titleLp);

        List<LedgerEntry> entries = db.recent(5, null);
        if (entries.isEmpty()) page.addView(emptyState());
        else {
            LinearLayout list = card();
            for (LedgerEntry entry : entries) list.addView(entryRow(entry));
            page.addView(list);
        }
        return scroll;
    }

    private View buildAutoBanner() {
        boolean access = hasNotificationAccess();
        LinearLayout banner = row();
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(15), dp(13), dp(15), dp(13));
        banner.setBackground(round(Color.WHITE, 15));
        TextView icon = text(access ? "✓" : "!", 17, access ? INK : 0xFFF59E0B, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(SUB, 20));
        banner.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout copy = column();
        copy.setPadding(dp(12), 0, dp(6), 0);
        copy.addView(text("自动记账", 13, INK, true));
        copy.addView(text(access ? "● 运行中 · 仅在本机处理" : "未开启 · 点击右侧授权", 11, access ? GREEN : MUTED, true));
        banner.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (!access) {
            TextView go = text("去开启", 13, BRAND, true);
            go.setOnClickListener(v -> openNotificationSettings());
            banner.addView(go);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        banner.setLayoutParams(lp);
        return banner;
    }

    private View buildEntries() {
        LinearLayout outer = column();
        outer.setBackgroundColor(PAGE);
        outer.setPadding(0, dp(14), 0, 0);
        LinearLayout heading = row();
        heading.setPadding(dp(20), 0, dp(20), dp(8));
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text("账单明细", 27, INK, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        heading.addView(text(db.monthCount() + " 笔", 13, MUTED, false));
        outer.addView(heading);

        HorizontalScrollView chips = new HorizontalScrollView(this);
        chips.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = row();
        chipRow.setPadding(dp(16), dp(8), dp(16), dp(12));
        String[] filters = {"全部", "餐饮", "交通", "购物", "居住", "娱乐", "其他", "收入"};
        for (String filter : filters) {
            boolean active = filter.equals(selectedCategory);
            TextView chip = text(filter, 13, active ? INK : MUTED, active);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(round(active ? BRAND : Color.WHITE, 18));
            chip.setOnClickListener(v -> {
                selectedCategory = ((TextView) v).getText().toString();
                showPage(1);
            });
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(dp(64), dp(36));
            chipLp.setMargins(dp(4), 0, dp(4), 0);
            chipRow.addView(chip, chipLp);
        }
        chips.addView(chipRow);
        outer.addView(chips);

        ScrollView scroll = scroll();
        LinearLayout list = page();
        list.setPadding(dp(16), dp(4), dp(16), dp(24));
        List<LedgerEntry> entries = db.recent(1000, selectedCategory);
        if (entries.isEmpty()) list.addView(emptyState());
        else for (LedgerEntry entry : entries) list.addView(entryRow(entry));
        scroll.addView(list);
        outer.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return outer;
    }

    private View buildStats() {
        ScrollView scroll = scroll();
        LinearLayout page = page();
        scroll.addView(page);
        page.addView(text("本月统计", 27, INK, true));
        long total = db.monthTotal(false);
        TextView amount = text(formatMoney(total, false), 36, INK, true);
        LinearLayout.LayoutParams amountLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        amountLp.setMargins(0, dp(14), 0, dp(4));
        page.addView(amount, amountLp);
        page.addView(text("本月总支出 · " + db.monthCount() + " 笔记录", 13, MUTED, false));

        LinearLayout categoryCard = card();
        categoryCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, dp(22), 0, dp(16));
        page.addView(categoryCard, cardLp);
        categoryCard.addView(text("分类支出", 18, INK, true));
        String[] expenseCategories = {"餐饮", "交通", "购物", "居住", "娱乐", "医疗", "学习", "其他"};
        boolean any = false;
        for (String category : expenseCategories) {
            long value = db.categoryMonthTotal(category);
            if (value == 0) continue;
            any = true;
            categoryCard.addView(categoryBar(category, value, total));
        }
        if (!any) {
            TextView hint = text("有账单后，这里会展示各分类占比", 14, MUTED, false);
            hint.setPadding(0, dp(22), 0, dp(14));
            categoryCard.addView(hint);
        }

        LinearLayout tip = card();
        tip.setPadding(dp(18), dp(16), dp(18), dp(16));
        tip.addView(text("记账小贴士", 15, BRAND, true));
        tip.addView(text("长按任意账单可以删除错误记录；自动分类不准确时，可先删除再手动补记。", 13, MUTED, false));
        page.addView(tip);
        return scroll;
    }

    private View buildSettings() {
        ScrollView scroll = scroll();
        LinearLayout page = page();
        scroll.addView(page);
        page.addView(text("设置", 27, INK, true));

        LinearLayout autoCard = card();
        autoCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topLp.setMargins(0, dp(20), 0, dp(14));
        page.addView(autoCard, topLp);
        LinearLayout switchRow = row();
        switchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout switchCopy = column();
        switchCopy.addView(text("自动记账", 17, INK, true));
        switchCopy.addView(text("从支付与银行通知识别交易", 13, MUTED, false));
        switchRow.addView(switchCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch toggle = new Switch(this);
        toggle.setChecked(getSharedPreferences("miaoji_settings", MODE_PRIVATE).getBoolean("auto_enabled", true));
        toggle.setOnCheckedChangeListener((button, checked) ->
                getSharedPreferences("miaoji_settings", MODE_PRIVATE).edit().putBoolean("auto_enabled", checked).apply());
        switchRow.addView(toggle);
        autoCard.addView(switchRow);

        View divider = new View(this);
        divider.setBackgroundColor(0xFFEDEEF3);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerLp.setMargins(0, dp(16), 0, dp(14));
        autoCard.addView(divider, dividerLp);

        TextView permission = text(hasNotificationAccess() ? "通知访问权限 · 已授权" : "通知访问权限 · 未授权", 14,
                hasNotificationAccess() ? GREEN : 0xFFF59E0B, true);
        permission.setPadding(0, dp(7), 0, dp(7));
        permission.setOnClickListener(v -> openNotificationSettings());
        autoCard.addView(permission);

        LinearLayout privacy = card();
        privacy.setPadding(dp(18), dp(18), dp(18), dp(18));
        privacy.addView(text("隐私保护", 17, INK, true));
        TextView privacyCopy = text("所有通知解析均在你的手机上完成。妙记不联网、不读取历史通知，也不会保存无关通知的正文。账本数据库不参与云备份。", 13, MUTED, false);
        privacyCopy.setLineSpacing(0, 1.25f);
        privacyCopy.setPadding(0, dp(8), 0, 0);
        privacy.addView(privacyCopy);
        LinearLayout.LayoutParams privacyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        privacyLp.setMargins(0, 0, 0, dp(14));
        page.addView(privacy, privacyLp);

        Button export = button("导出 CSV 备份");
        export.setOnClickListener(v -> startCsvExport());
        page.addView(export);

        TextView version = text("妙记 1.0.0 · 数据只属于你", 12, MUTED, false);
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, dp(24), 0, dp(8));
        page.addView(version);
        return scroll;
    }

    private View categoryBar(String category, long value, long total) {
        LinearLayout box = column();
        box.setPadding(0, dp(15), 0, 0);
        LinearLayout labels = row();
        labels.addView(text(category, 14, INK, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        labels.addView(text(formatMoney(value, false), 14, INK, true));
        box.addView(labels);
        FrameLayout track = new FrameLayout(this);
        track.setBackground(round(SUB, 4));
        View fill = new View(this);
        fill.setBackground(round(BRAND, 4));
        float ratio = total <= 0 ? 0 : Math.max(0.04f, (float) value / total);
        track.addView(fill, new FrameLayout.LayoutParams(0, dp(7)));
        track.post(() -> {
            ViewGroup.LayoutParams params = fill.getLayoutParams();
            params.width = (int) (track.getWidth() * ratio);
            fill.setLayoutParams(params);
        });
        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7));
        trackLp.setMargins(0, dp(7), 0, 0);
        box.addView(track, trackLp);
        return box;
    }

    private View entryRow(LedgerEntry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        row.setBackground(round(Color.WHITE, 18));
        row.setElevation(dp(1));

        TextView icon = text(categoryEmoji(entry.category), 14, INK, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(SUB, 23));
        row.addView(icon, new LinearLayout.LayoutParams(dp(45), dp(45)));
        LinearLayout detail = column();
        detail.setPadding(dp(12), 0, dp(8), 0);
        detail.addView(text(entry.merchant, 14, INK, true));
        String when = new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(new Date(entry.occurredAt));
        detail.addView(text(entry.category + " · " + entry.source + " · " + when, 11, MUTED, false));
        row.addView(detail, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(text((entry.income ? "+" : "−") + formatMoney(entry.amountCents, false), 14,
                entry.income ? GREEN : INK, true));
        row.setOnLongClickListener(v -> {
            confirmDelete(entry);
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(lp);
        return row;
    }

    private View emptyState() {
        LinearLayout empty = column();
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(20), dp(36), dp(20), dp(36));
        empty.setBackground(round(Color.WHITE, 20));
        empty.addView(text("🧾", 34, INK, false));
        TextView title = text("还没有账单", 17, INK, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(10), 0, dp(3));
        empty.addView(title);
        TextView copy = text("点下方 ＋ 手动记一笔，或开启自动记账", 13, MUTED, false);
        copy.setGravity(Gravity.CENTER);
        empty.addView(copy);
        return empty;
    }

    private void showAddDialog() {
        LinearLayout form = column();
        form.setPadding(dp(20), dp(4), dp(20), 0);
        EditText amount = input("金额，例如 28.50");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(amount);
        EditText merchant = input("商户或事项");
        form.addView(merchant);

        Spinner category = new Spinner(this);
        category.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, CATEGORIES));
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        spinnerLp.setMargins(0, dp(8), 0, 0);
        form.addView(category, spinnerLp);

        new AlertDialog.Builder(this)
                .setTitle("记一笔")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    try {
                        String categoryName = category.getSelectedItem().toString();
                        long cents = Math.round(Double.parseDouble(amount.getText().toString().trim()) * 100d);
                        String merchantName = merchant.getText().toString().trim();
                        if (cents <= 0 || merchantName.isEmpty()) throw new IllegalArgumentException();
                        boolean income = "收入".equals(categoryName);
                        LedgerEntry entry = new LedgerEntry(cents, income, merchantName, categoryName,
                                System.currentTimeMillis(), "手动记账", "manual-" + UUID.randomUUID(), "");
                        db.insert(entry);
                        showPage(currentPage);
                    } catch (Exception error) {
                        Toast.makeText(this, "请填写正确的金额和事项", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void confirmDelete(LedgerEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle("删除这笔账单？")
                .setMessage(entry.merchant + "  " + formatMoney(entry.amountCents, false))
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    db.delete(entry.id);
                    showPage(currentPage);
                }).show();
    }

    private void startCsvExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "妙记账单-" + new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date()) + ".csv");
        startActivityForResult(intent, EXPORT_CSV);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_CSV || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("No output stream");
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            out.write("时间,类型,金额,分类,商户,来源\n".getBytes(StandardCharsets.UTF_8));
            SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
            for (LedgerEntry entry : db.recent(100000, null)) {
                String line = csv(date.format(new Date(entry.occurredAt))) + "," +
                        csv(entry.income ? "收入" : "支出") + "," +
                        String.format(Locale.ROOT, "%.2f", entry.amountCents / 100d) + "," +
                        csv(entry.category) + "," + csv(entry.merchant) + "," + csv(entry.source) + "\n";
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
            Toast.makeText(this, "CSV 已导出", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "导出失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasNotificationAccess() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        String own = new ComponentName(this, AutoBookkeepingService.class).flattenToString();
        return enabled.contains(own) || enabled.contains(getPackageName());
    }

    private void openNotificationSettings() {
        startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
    }

    private LinearLayout summaryMetric(String label, String value) {
        LinearLayout metric = column();
        metric.addView(text(label, 12, 0xA6000D08, false));
        metric.addView(text(value, 14, INK, true));
        return metric;
    }

    private ScrollView scroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        return scroll;
    }

    private LinearLayout page() {
        LinearLayout page = column();
        page.setPadding(dp(13), dp(10), dp(13), dp(24));
        return page;
    }

    private LinearLayout card() {
        LinearLayout card = column();
        card.setBackground(round(Color.WHITE, 20));
        return card;
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
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setPadding(dp(12), dp(5), dp(12), dp(5));
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(BRAND));
        input.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        return input;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(INK);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setBackground(round(BRAND, 18));
        button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        return button;
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

    private String formatMoney(long cents, boolean signed) {
        String prefix = signed && cents < 0 ? "−" : "";
        return prefix + "¥" + String.format(Locale.CHINA, "%,.2f", Math.abs(cents) / 100d);
    }

    private String categoryEmoji(String category) {
        switch (category) {
            case "餐饮": return "餐";
            case "交通": return "行";
            case "购物": return "购";
            case "居住": return "居";
            case "娱乐": return "娱";
            case "医疗": return "医";
            case "学习": return "学";
            case "收入": return "收";
            default: return "其";
        }
    }

    private int categoryColor(String category) {
        switch (category) {
            case "餐饮": return 0xFFFFF0E8;
            case "交通": return 0xFFE8F2FF;
            case "购物": return 0xFFFFEAF4;
            case "居住": return 0xFFEAF8EF;
            case "娱乐": return 0xFFF1EBFF;
            case "收入": return 0xFFE7F8EC;
            default: return 0xFFF0F1F5;
        }
    }

    private String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
