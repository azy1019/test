package com.miaoji.ledger;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure-Java parser so its privacy-sensitive behavior can be tested without Android. */
public final class NotificationTransactionParser {
    private static final Pattern[] AMOUNT_PATTERNS = new Pattern[] {
            Pattern.compile("(?:支付|付款|消费|支出|扣款|收款|到账|入账|金额)[成功为共：: ]*(?:人民币)?[￥¥]?\\s*((?:\\d{1,3}(?:,\\d{3})+|\\d{1,8})(?:\\.\\d{1,2})?)\\s*元?"),
            Pattern.compile("[￥¥]\\s*((?:\\d{1,3}(?:,\\d{3})+|\\d{1,8})(?:\\.\\d{1,2})?)"),
            Pattern.compile("((?:\\d{1,3}(?:,\\d{3})+|\\d{1,8})(?:\\.\\d{1,2})?)\\s*元(?:已|，|,|。|$)")
    };
    private static final Pattern MERCHANT_TO = Pattern.compile("向[“\"']?([^，,。；;]{2,24}?)[”\"']?(?:付款|支付|转账)");
    private static final Pattern MERCHANT_AT = Pattern.compile("(?:商户|对方|收款方)[：:]\\s*([^，,。；;]{2,24})");
    private static final Pattern MERCHANT_SPEND = Pattern.compile("(?:在|于)[“\"']?([^，,。；;]{2,24}?)[”\"']?(?:消费|支出|付款)");
    private static final String[] PAYMENT_WORDS = {
            "支付成功", "付款成功", "消费", "支出", "扣款", "收款", "到账", "入账", "转账成功", "交易成功"
    };
    private static final String[] INCOME_WORDS = {
            "收款", "到账", "入账", "收入", "退款成功", "已退款", "转入"
    };

    private NotificationTransactionParser() {}

    public static LedgerEntry parse(String packageName, String title, String text, long postedAt) {
        String safeTitle = clean(title);
        String safeText = clean(text);
        String all = safeTitle + " " + safeText;
        if (!isLikelyFinancialSource(packageName, safeTitle, safeText)) return null;
        if (!containsAny(all, PAYMENT_WORDS)) return null;

        BigDecimal amount = findAmount(all);
        if (amount == null || amount.signum() <= 0) return null;

        boolean income = containsAny(all, INCOME_WORDS) && !all.contains("向") && !all.contains("付款成功");
        String merchant = findMerchant(all, safeTitle);
        String category = classify(merchant + " " + all, income);
        long cents;
        try {
            cents = amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException ex) {
            return null;
        }
        String source = sourceName(packageName, safeTitle);
        String fingerprint = sha256(packageName + "|" + safeTitle + "|" + safeText + "|" + (postedAt / 60000L));
        return new LedgerEntry(cents, income, merchant, category, postedAt, source, fingerprint, safeText);
    }

    private static BigDecimal findAmount(String all) {
        for (Pattern pattern : AMOUNT_PATTERNS) {
            Matcher matcher = pattern.matcher(all);
            while (matcher.find()) {
                String prefix = all.substring(Math.max(0, matcher.start() - 8), matcher.start());
                if (prefix.contains("余额") || prefix.contains("可用")) continue;
                try {
                    return new BigDecimal(matcher.group(1).replace(",", ""));
                } catch (NumberFormatException ignored) {
                    // Try the next match.
                }
            }
        }
        return null;
    }

    private static String findMerchant(String all, String title) {
        for (Pattern pattern : new Pattern[]{MERCHANT_TO, MERCHANT_AT, MERCHANT_SPEND}) {
            Matcher matcher = pattern.matcher(all);
            if (matcher.find()) return trimMerchant(matcher.group(1));
        }
        if (!title.isEmpty() && !isGenericTitle(title)) return trimMerchant(title);
        return "未识别商户";
    }

    private static String trimMerchant(String value) {
        String result = value.replaceAll("^(您已|已使用|通过|使用)", "").trim();
        return result.length() > 24 ? result.substring(0, 24) : result;
    }

    private static boolean isGenericTitle(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        return lower.contains("微信支付") || lower.contains("支付宝") || lower.contains("交易提醒")
                || lower.contains("银行") || lower.contains("支付通知");
    }

    public static String classify(String text, boolean income) {
        if (income) return "收入";
        String[][] rules = {
                {"餐饮", "餐", "饭", "咖啡", "奶茶", "美团", "饿了么", "餐厅", "面包", "食堂"},
                {"交通", "地铁", "公交", "滴滴", "打车", "高德", "铁路", "航旅", "加油", "停车"},
                {"购物", "淘宝", "天猫", "京东", "拼多多", "超市", "便利店", "商场", "服饰"},
                {"居住", "房租", "物业", "水费", "电费", "燃气", "宽带"},
                {"娱乐", "电影", "游戏", "视频", "音乐", "门票", "KTV"},
                {"医疗", "医院", "药房", "诊所", "挂号", "体检"},
                {"学习", "书店", "课程", "培训", "教育", "文具"}
        };
        for (String[] rule : rules) {
            for (int i = 1; i < rule.length; i++) {
                if (text.contains(rule[i])) return rule[0];
            }
        }
        return "其他";
    }

    private static String sourceName(String packageName, String title) {
        if ("com.tencent.mm".equals(packageName)) return "微信支付";
        if ("com.eg.android.AlipayGphone".equals(packageName)) return "支付宝";
        if (packageName != null && (packageName.contains("bank") || packageName.contains("mobile"))) return "银行卡";
        return title.isEmpty() ? "通知识别" : title;
    }

    private static boolean isLikelyFinancialSource(String packageName, String title, String text) {
        String pkg = packageName == null ? "" : packageName.toLowerCase(Locale.ROOT);
        if ("com.tencent.mm".equals(pkg)) {
            return title.contains("微信支付") || title.contains("支付通知") || text.contains("微信支付");
        }
        if ("com.eg.android.alipaygphone".equals(pkg)) return true;
        if (pkg.contains("bank") || pkg.contains("creditcard")) return true;
        return title.contains("银行") || title.contains("交易提醒") || title.contains("支付通知")
                || title.contains("扣款提醒") || title.contains("收款通知");
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private static boolean containsAny(String value, String[] needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] data = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte item : data) out.append(String.format(Locale.ROOT, "%02x", item));
            return out.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
