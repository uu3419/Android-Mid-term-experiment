/*
 * Copyright (C) 2007 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 *
 * 扩展说明：增加分类 Spinner，创建/编辑时保存 category 字段。
 */
package com.example.android.notepad;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@SuppressWarnings("deprecation")
public class NoteEditor extends Activity {

    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE,
            NotePad.Notes.COLUMN_NAME_CATEGORY,
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
    };
    private static final int COLUMN_INDEX_TITLE = 1;
    private static final int COLUMN_INDEX_NOTE = 2;
    private static final int COLUMN_INDEX_CATEGORY = 3;
    private static final int COLUMN_INDEX_MODIFIED = 4;

    private Uri mUri;
    private Cursor mCursor;
    private EditText mText;
    private Spinner mCategorySpinner;
    private String mOriginalContent;

    // 可选分类
    private static final String[] CATEGORIES = {
            NotePad.Notes.CATEGORY_UNCATEGORIZED,
            "工作", "个人", "灵感"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        final String action = intent.getAction();

        if (Intent.ACTION_EDIT.equals(action)) {
            setTitle(R.string.title_edit);
            mUri = intent.getData();
        } else if (Intent.ACTION_INSERT.equals(action)
                || Intent.ACTION_PASTE.equals(action)) {
            setTitle(R.string.title_create);
            ContentValues values = new ContentValues();
            values.put(NotePad.Notes.COLUMN_NAME_TITLE,
                    getString(R.string.title_create));
            values.put(NotePad.Notes.COLUMN_NAME_NOTE, "");
            Uri target = intent.getData();
            if (target == null) {
                target = NotePad.Notes.CONTENT_URI;
            }
            mUri = getContentResolver().insert(target, values);
        } else {
            setTitle(R.string.title_view);
            mUri = intent.getData();
        }

        setContentView(R.layout.note_editor);
        mText = findViewById(R.id.note);
        mCategorySpinner = findViewById(R.id.spinner_category);
        // 设置 Spinner 选项
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, CATEGORIES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCategorySpinner.setAdapter(adapter);

        mCursor = managedQuery(mUri, PROJECTION, null, null, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mCursor != null && mCursor.moveToFirst()) {
            mText.setText(mCursor.getString(COLUMN_INDEX_NOTE));
            setTitle(getString(R.string.title_edit,
                    mCursor.getString(COLUMN_INDEX_TITLE)));
            mOriginalContent = mCursor.getString(COLUMN_INDEX_NOTE);

            // 恢复分类选择
            String savedCat = mCursor.getString(COLUMN_INDEX_CATEGORY);
            if (savedCat != null) {
                for (int i = 0; i < CATEGORIES.length; i++) {
                    if (CATEGORIES[i].equals(savedCat)) {
                        mCategorySpinner.setSelection(i);
                        break;
                    }
                }
            }

            // UI 美化：右上角显示最后修改时间
            TextView tvModified = findViewById(R.id.tv_modified);
            if (tvModified != null) {
                long modified = mCursor.getLong(COLUMN_INDEX_MODIFIED);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
                tvModified.setText(getString(R.string.last_modified, sdf.format(new Date(modified))));
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCursor != null) {
            ContentValues values = new ContentValues();
            values.put(NotePad.Notes.COLUMN_NAME_NOTE, mText.getText().toString());
            values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                    System.currentTimeMillis());
            // 扩展：保存分类
            values.put(NotePad.Notes.COLUMN_NAME_CATEGORY,
                    (String) mCategorySpinner.getSelectedItem());
            getContentResolver().update(mUri, values, null, null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_save) {
            save();
            finish();
            return true;
        } else if (id == R.id.menu_delete) {
            deleteNote();
            finish();
            return true;
        } else if (id == R.id.menu_revert) {
            revert();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void save() {
        if (mUri != null) {
            ContentValues values = new ContentValues();
            values.put(NotePad.Notes.COLUMN_NAME_NOTE, mText.getText().toString());
            values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                    System.currentTimeMillis());
            values.put(NotePad.Notes.COLUMN_NAME_CATEGORY,
                    (String) mCategorySpinner.getSelectedItem());
            getContentResolver().update(mUri, values, null, null);
        }
    }

    private void deleteNote() {
        if (mUri != null) {
            getContentResolver().delete(mUri, null, null);
        }
    }

    private void revert() {
        if (mOriginalContent != null && mText != null) {
            mText.setText(mOriginalContent);
        }
    }

    @SuppressLint("AppCompatCustomView")
    public static class LinedEditText extends EditText {
        private Rect mRect;
        private Paint mPaint;

        public LinedEditText(Context context, AttributeSet attrs) {
            super(context, attrs);
            mRect = new Rect();
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.STROKE);
            // UI 美化：行线改为淡蓝色，避免干扰正文
            mPaint.setColor(0x332196F3);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Rect r = mRect;
            Paint paint = mPaint;
            int count = getLineCount();
            for (int i = 0; i < count; i++) {
                int baseline = getLineBounds(i, r);
                canvas.drawLine(r.left, baseline + 1, r.right, baseline + 1, paint);
            }
            super.onDraw(canvas);
        }
    }
}
