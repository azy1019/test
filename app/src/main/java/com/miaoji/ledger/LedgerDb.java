package com.miaoji.ledger;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public final class LedgerDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "ledger.db";
    private static final int DB_VERSION = 1;

    public LedgerDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "amount_cents INTEGER NOT NULL," +
                "is_income INTEGER NOT NULL DEFAULT 0," +
                "merchant TEXT NOT NULL," +
                "category TEXT NOT NULL," +
                "occurred_at INTEGER NOT NULL," +
                "source TEXT NOT NULL," +
                "fingerprint TEXT UNIQUE," +
                "note TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX entries_time ON entries(occurred_at DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public long insert(LedgerEntry entry) {
        ContentValues values = new ContentValues();
        values.put("amount_cents", entry.amountCents);
        values.put("is_income", entry.income ? 1 : 0);
        values.put("merchant", entry.merchant);
        values.put("category", entry.category);
        values.put("occurred_at", entry.occurredAt);
        values.put("source", entry.source);
        values.put("fingerprint", entry.fingerprint);
        values.put("note", entry.note == null ? "" : entry.note);
        return getWritableDatabase().insertWithOnConflict("entries", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public List<LedgerEntry> recent(int limit, String category) {
        List<LedgerEntry> entries = new ArrayList<>();
        String selection = category == null || "全部".equals(category) ? null : "category=?";
        String[] args = selection == null ? null : new String[]{category};
        try (Cursor cursor = getReadableDatabase().query("entries", null, selection, args,
                null, null, "occurred_at DESC", String.valueOf(limit))) {
            while (cursor.moveToNext()) entries.add(read(cursor));
        }
        return entries;
    }

    public long monthTotal(boolean income) {
        long start = monthStart();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount_cents),0) FROM entries WHERE is_income=? AND occurred_at>=?",
                new String[]{income ? "1" : "0", String.valueOf(start)})) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    public long categoryMonthTotal(String category) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount_cents),0) FROM entries WHERE is_income=0 AND category=? AND occurred_at>=?",
                new String[]{category, String.valueOf(monthStart())})) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    public int monthCount() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM entries WHERE occurred_at>=?", new String[]{String.valueOf(monthStart())})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public void delete(long id) {
        getWritableDatabase().delete("entries", "id=?", new String[]{String.valueOf(id)});
    }

    private LedgerEntry read(Cursor cursor) {
        LedgerEntry entry = new LedgerEntry(
                cursor.getLong(cursor.getColumnIndexOrThrow("amount_cents")),
                cursor.getInt(cursor.getColumnIndexOrThrow("is_income")) == 1,
                cursor.getString(cursor.getColumnIndexOrThrow("merchant")),
                cursor.getString(cursor.getColumnIndexOrThrow("category")),
                cursor.getLong(cursor.getColumnIndexOrThrow("occurred_at")),
                cursor.getString(cursor.getColumnIndexOrThrow("source")),
                cursor.getString(cursor.getColumnIndexOrThrow("fingerprint")),
                cursor.getString(cursor.getColumnIndexOrThrow("note")));
        entry.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        return entry;
    }

    private long monthStart() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
