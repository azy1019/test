package com.miaoji.ledger;

public final class LedgerEntry {
    public long id;
    public long amountCents;
    public boolean income;
    public String merchant;
    public String category;
    public long occurredAt;
    public String source;
    public String fingerprint;
    public String note;

    public LedgerEntry(long amountCents, boolean income, String merchant, String category,
                       long occurredAt, String source, String fingerprint, String note) {
        this.amountCents = amountCents;
        this.income = income;
        this.merchant = merchant;
        this.category = category;
        this.occurredAt = occurredAt;
        this.source = source;
        this.fingerprint = fingerprint;
        this.note = note;
    }
}
