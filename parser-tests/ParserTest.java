import com.miaoji.ledger.LedgerEntry;
import com.miaoji.ledger.NotificationTransactionParser;

public final class ParserTest {
    private static int passed;

    public static void main(String[] args) {
        LedgerEntry wechat = NotificationTransactionParser.parse(
                "com.tencent.mm", "微信支付", "支付成功 ¥28.50 商户：午后咖啡", 1723300000000L);
        check(wechat != null, "微信支付通知应被识别");
        check(wechat.amountCents == 2850, "金额应为 2850 分");
        check("餐饮".equals(wechat.category), "咖啡应归类为餐饮");
        check("午后咖啡".equals(wechat.merchant), "应提取商户");

        LedgerEntry alipay = NotificationTransactionParser.parse(
                "com.eg.android.AlipayGphone", "支付宝", "向滴滴出行付款成功 16.80元", 1723300060000L);
        check(alipay != null && alipay.amountCents == 1680, "支付宝付款应被识别");
        check("交通".equals(alipay.category), "滴滴应归类为交通");

        LedgerEntry income = NotificationTransactionParser.parse(
                "com.example.bank", "某某银行交易提醒", "工资入账：人民币 8,000.00 元", 1723300120000L);
        check(income != null && income.amountCents == 800000, "银行入账与千分位金额应被识别");
        check(income.income && "收入".equals(income.category), "工资入账应归类为收入");

        LedgerEntry unrelated = NotificationTransactionParser.parse(
                "com.tencent.mm", "朋友", "晚上吃饭吗？AA 50 元", 1723300180000L);
        check(unrelated == null, "普通聊天不能被记账");

        LedgerEntry fakePaymentChat = NotificationTransactionParser.parse(
                "com.tencent.mm", "朋友", "我这里显示支付成功 50.00 元", 1723300190000L);
        check(fakePaymentChat == null, "包含支付字样的聊天也不能被记账");

        LedgerEntry balance = NotificationTransactionParser.parse(
                "com.example.bank", "交易提醒", "消费 30.00 元，账户余额 5000.00 元", 1723300240000L);
        check(balance != null && balance.amountCents == 3000, "应识别消费金额而不是余额");

        System.out.println("Parser tests passed: " + passed);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        passed++;
    }
}
