/*
 * Copyright (C) 2007 The Android Open Source Project
 */
package com.example.android.notepad;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

/**
 * 官方 TitleEditor：在对话框中编辑单条笔记的 title 字段。内容仍保留原始实现，
 * 后续扩展不会改动该 Activity 的职责——仍然只改 title 字段。
 */
public class TitleEditor extends Activity {
    public static final String EDIT_TITLE_ACTION = "com.android.notepad.action.EDIT_TITLE";

    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
    };
    private static final int COLUMN_INDEX_TITLE = 1;

    private Cursor mCursor;
    private EditText mText;
    private Uri mUri;

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.title_editor);
        mUri = getIntent().getData();
        mCursor = managedQuery(mUri, PROJECTION, null, null, null);
        mText = (EditText) this.findViewById(R.id.title);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mCursor != null) {
            mCursor.moveToFirst();
            mText.setText(mCursor.getString(COLUMN_INDEX_TITLE));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCursor != null) {
            ContentValues values = new ContentValues();
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, mText.getText().toString());
            getContentResolver().update(mUri, values, null, null);
        }
    }

    public void onClickOk(View v) {
        finish();
    }
}
