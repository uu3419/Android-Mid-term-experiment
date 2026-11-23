/*
 * Copyright (C) 2007 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 *
 * 扩展实现：
 *   - 基本扩展 1：列表时间戳（PROJECTION 加 MODIFICATION_DATE + ViewBinder 格式化）
 *   - 基本扩展 2：标题/内容搜索（顶部搜索框 + selection 过滤）
 *   - 附加功能 1：笔记分类（列表显示分类标签 + 按分类筛选）
 *   - 附加功能 2：排序切换（按修改时间 / 按标题）
 *   - 附加功能 3：UI 美化（主题配色、卡片式列表、圆角搜索框、编辑器卡片）
 *   - 附加功能 4：导出笔记 + 云备份与恢复（导出 txt 分享 / JSON 备份到云端目录 / 一键恢复）
 */
package com.example.android.notepad;

import com.example.android.notepad.NotePad;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("deprecation")
public class NotesList extends ListActivity {
    private static final String TAG = "NotesList";

    private static final int ACTIVITY_CREATE = 0;
    private static final int ACTIVITY_EDIT = 1;

    private static final String BACKUP_FILE_NAME = "notes_backup.json";
    private static final String FILE_PROVIDER_AUTHORITY = "com.example.android.notepad.fileprovider";

    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
            // 扩展：分类
            NotePad.Notes.COLUMN_NAME_CATEGORY,
    };

    private Cursor mNotesCursor;
    private SimpleCursorAdapter mAdapter;

    // 搜索 & 排序 & 分类筛选状态
    private String mSearchKeyword = "";
    private boolean mSortByTitle = false;
    private String mFilterCategory = null; // null 表示全部分类

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 布局：包含顶部搜索框 + 列表
        setContentView(R.layout.activity_notes_list);

        Intent intent = getIntent();
        if (intent.getAction() == Intent.ACTION_EDIT) {
            intent.setAction(Intent.ACTION_VIEW);
        }

        setupSearchBox();
        setNotesList();

        // UI 美化：右下角悬浮新建按钮
        ImageButton fab = (ImageButton) findViewById(R.id.btn_add_fab);
        if (fab != null) {
            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    createNote();
                }
            });
        }
    }

    private void setupSearchBox() {
        EditText searchBox = (EditText) findViewById(R.id.et_search);
        if (searchBox != null) {
            searchBox.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    mSearchKeyword = s != null ? s.toString().trim() : "";
                    refreshList();
                }
                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);
        // 排序切换菜单
        MenuItem sortItem = menu.findItem(R.id.menu_sort);
        if (sortItem != null) {
            sortItem.setTitle(mSortByTitle ? R.string.sort_by_modified : R.string.sort_by_title);
        }
        // 分类筛选菜单，标题显示当前筛选的分类
        MenuItem filterItem = menu.findItem(R.id.menu_filter);
        if (filterItem != null) {
            filterItem.setTitle(getString(R.string.filter_current,
                    mFilterCategory == null ? getString(R.string.filter_all) : mFilterCategory));
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_add) {
            createNote();
            return true;
        } else if (id == R.id.menu_sort) {
            mSortByTitle = !mSortByTitle;
            item.setTitle(mSortByTitle ? R.string.sort_by_modified : R.string.sort_by_title);
            refreshList();
            return true;
        } else if (id == R.id.menu_filter) {
            showCategoryFilter();
            return true;
        } else if (id == R.id.menu_export) {
            exportAllNotes();
            return true;
        } else if (id == R.id.menu_backup) {
            backupToCloud();
            return true;
        } else if (id == R.id.menu_restore) {
            restoreFromCloud();
            return true;
        } else if (id == R.id.menu_paste) {
            Intent intent = new Intent(Intent.ACTION_PASTE, NotePad.Notes.CONTENT_URI);
            intent.setClass(this, NoteEditor.class);
            startActivityForResult(intent, ACTIVITY_CREATE);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        long rowId = info != null ? info.id : -1;
        if (rowId < 0) return false;

        int clickedId = item.getItemId();
        if (clickedId == R.id.context_delete) {
            int count = getContentResolver().delete(
                    ContentUris.withAppendedId(NotePad.Notes.CONTENT_ID_URI_BASE, rowId),
                    null, null);
            if (count > 0) refreshList();
            return true;
        }
        // 默认打开
        openNote(rowId);
        return true;
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        openNote(id);
    }

    private void createNote() {
        Intent intent = new Intent(Intent.ACTION_INSERT, NotePad.Notes.CONTENT_URI);
        intent.setClass(this, NoteEditor.class);
        startActivityForResult(intent, ACTIVITY_CREATE);
    }

    private void openNote(long rowId) {
        Intent intent = new Intent(Intent.ACTION_VIEW,
                ContentUris.withAppendedId(NotePad.Notes.CONTENT_ID_URI_BASE, rowId));
        intent.setClass(this, NoteEditor.class);
        startActivityForResult(intent, ACTIVITY_EDIT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == ACTIVITY_CREATE || requestCode == ACTIVITY_EDIT)
                && resultCode == RESULT_OK) {
            refreshList();
        }
    }

    /** 组装 selection：关键字搜索 + 分类筛选 */
    private String buildSelection() {
        StringBuilder sb = new StringBuilder();
        if (mSearchKeyword.length() > 0) {
            sb.append("(").append(NotePad.Notes.COLUMN_NAME_TITLE).append(" LIKE ? OR ")
                    .append(NotePad.Notes.COLUMN_NAME_NOTE).append(" LIKE ?)");
        }
        if (mFilterCategory != null) {
            if (sb.length() > 0) {
                sb.append(" AND ");
            }
            sb.append(NotePad.Notes.COLUMN_NAME_CATEGORY).append(" = ?");
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private String[] buildSelectionArgs() {
        List<String> args = new ArrayList<String>();
        if (mSearchKeyword.length() > 0) {
            String like = "%" + mSearchKeyword + "%";
            args.add(like);
            args.add(like);
        }
        if (mFilterCategory != null) {
            args.add(mFilterCategory);
        }
        return args.isEmpty() ? null : args.toArray(new String[0]);
    }

    private String getSortOrder() {
        return mSortByTitle
                ? NotePad.Notes.SORT_ORDER_BY_TITLE
                : NotePad.Notes.DEFAULT_SORT_ORDER;
    }

    /** 分类标签配色 */
    private int categoryColor(String category) {
        if (getString(R.string.category_work).equals(category)) {
            return 0xFF1565C0;
        } else if (getString(R.string.category_personal).equals(category)) {
            return 0xFF2E7D32;
        } else if (getString(R.string.category_idea).equals(category)) {
            return 0xFFEF6C00;
        }
        return 0xFF757575;
    }

    /** 分类筛选对话框 */
    private void showCategoryFilter() {
        final String[] options = new String[] {
                getString(R.string.filter_all),
                NotePad.Notes.CATEGORY_UNCATEGORIZED,
                getString(R.string.category_work),
                getString(R.string.category_personal),
                getString(R.string.category_idea)
        };
        int checked = 0;
        if (mFilterCategory != null) {
            for (int i = 1; i < options.length; i++) {
                if (options[i].equals(mFilterCategory)) {
                    checked = i;
                    break;
                }
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.filter_title)
                .setSingleChoiceItems(options, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mFilterCategory = which == 0 ? null : options[which];
                        refreshList();
                        invalidateOptionsMenu();
                        dialog.dismiss();
                    }
                })
                .show();
    }

    /** 附加功能 4：导出全部笔记为 txt，并通过系统分享面板发送 */
    private void exportAllNotes() {
        Cursor cursor = getContentResolver().query(NotePad.Notes.CONTENT_URI,
                new String[] {
                        NotePad.Notes.COLUMN_NAME_TITLE,
                        NotePad.Notes.COLUMN_NAME_NOTE,
                        NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                        NotePad.Notes.COLUMN_NAME_CATEGORY
                },
                null, null, NotePad.Notes.DEFAULT_SORT_ORDER);
        if (cursor == null || cursor.getCount() == 0) {
            if (cursor != null) {
                cursor.close();
            }
            Toast.makeText(this, R.string.export_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        StringBuilder sb = new StringBuilder();
        sb.append("NotePad 笔记导出\n导出时间：").append(sdf.format(new Date()))
                .append("\n笔记数量：").append(cursor.getCount()).append("\n");
        int index = 1;
        while (cursor.moveToNext()) {
            sb.append("\n---------------- 第 ").append(index++).append(" 条 ----------------\n");
            sb.append("标题：").append(cursor.getString(0)).append("\n");
            sb.append("分类：").append(cursor.getString(3)).append("\n");
            sb.append("修改时间：").append(sdf.format(new Date(cursor.getLong(2)))).append("\n\n");
            sb.append(cursor.getString(1)).append("\n");
        }
        cursor.close();

        try {
            File dir = new File(getCacheDir(), "exports");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("create export dir failed");
            }
            File file = new File(dir, "notepad_export_" + System.currentTimeMillis() + ".txt");
            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");
            writer.write(sb.toString());
            writer.close();

            Uri uri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_chooser));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.export_chooser)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.export_fail, Toast.LENGTH_SHORT).show();
        }
    }

    /** 云备份目录：应用外部私有目录下的 cloud/，名为 notes_backup.json */
    private File getCloudBackupFile() {
        File dir = new File(getExternalFilesDir(null), "cloud");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, BACKUP_FILE_NAME);
    }

    /** 附加功能 4：把全部笔记备份为 JSON */
    private void backupToCloud() {
        Cursor cursor = getContentResolver().query(NotePad.Notes.CONTENT_URI,
                new String[] {
                        NotePad.Notes.COLUMN_NAME_TITLE,
                        NotePad.Notes.COLUMN_NAME_NOTE,
                        NotePad.Notes.COLUMN_NAME_CREATE_DATE,
                        NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                        NotePad.Notes.COLUMN_NAME_CATEGORY
                },
                null, null, NotePad.Notes.DEFAULT_SORT_ORDER);
        if (cursor == null) {
            Toast.makeText(this, R.string.backup_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        if (cursor.getCount() == 0) {
            cursor.close();
            Toast.makeText(this, R.string.backup_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONArray array = new JSONArray();
            while (cursor.moveToNext()) {
                JSONObject item = new JSONObject();
                item.put("title", cursor.getString(0));
                item.put("note", cursor.getString(1));
                item.put("created", cursor.getLong(2));
                item.put("modified", cursor.getLong(3));
                item.put("category", cursor.getString(4));
                array.put(item);
            }
            File file = getCloudBackupFile();
            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");
            writer.write(array.toString());
            writer.close();
            Toast.makeText(this,
                    getString(R.string.backup_done, array.length(), file.getAbsolutePath()),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.backup_fail, Toast.LENGTH_SHORT).show();
        } finally {
            cursor.close();
        }
    }

    /** 附加功能 4：从云端备份文件恢复笔记（追加到现有列表） */
    private void restoreFromCloud() {
        final File file = getCloudBackupFile();
        if (!file.exists()) {
            Toast.makeText(this, R.string.restore_missing, Toast.LENGTH_LONG).show();
            return;
        }
        JSONArray backup;
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            backup = new JSONArray(sb.toString());
        } catch (Exception e) {
            Toast.makeText(this, R.string.restore_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        final JSONArray items = backup;
        new AlertDialog.Builder(this)
                .setTitle(R.string.restore_title)
                .setMessage(getString(R.string.restore_message, items.length()))
                .setPositiveButton(R.string.button_restore, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int restored = 0;
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.optJSONObject(i);
                            if (item == null) {
                                continue;
                            }
                            long now = System.currentTimeMillis();
                            ContentValues values = new ContentValues();
                            values.put(NotePad.Notes.COLUMN_NAME_TITLE, item.optString("title", ""));
                            values.put(NotePad.Notes.COLUMN_NAME_NOTE, item.optString("note", ""));
                            values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, item.optLong("created", now));
                            values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                                    item.optLong("modified", now));
                            values.put(NotePad.Notes.COLUMN_NAME_CATEGORY,
                                    item.optString("category", NotePad.Notes.CATEGORY_UNCATEGORIZED));
                            getContentResolver().insert(NotePad.Notes.CONTENT_URI, values);
                            restored++;
                        }
                        refreshList();
                        Toast.makeText(NotesList.this,
                                getString(R.string.restore_done, restored), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private void setNotesList() {
        mNotesCursor = managedQuery(NotePad.Notes.CONTENT_URI, PROJECTION,
                buildSelection(), buildSelectionArgs(), getSortOrder());

        mAdapter = new SimpleCursorAdapter(this,
                R.layout.noteslist_item,
                mNotesCursor,
                new String[] {
                        NotePad.Notes.COLUMN_NAME_TITLE,
                        NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                        NotePad.Notes.COLUMN_NAME_CATEGORY,
                        NotePad.Notes.COLUMN_NAME_CATEGORY
                },
                new int[] { android.R.id.text1, R.id.tv_time, R.id.tv_category, R.id.v_stripe },
                SimpleCursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);

        // 基本扩展 1：ViewBinder 格式化时间戳；附加功能 1：分类标签配色
        mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);

            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (view.getId() == R.id.tv_time) {
                    long t = cursor.getLong(columnIndex);
                    ((TextView) view).setText(getString(R.string.modified_prefix, sdf.format(new Date(t))));
                    return true;
                }
                if (view.getId() == R.id.tv_category) {
                    int color = categoryColor(cursor.getString(columnIndex));
                    // UI 美化：分类标签使用同色系淡色底
                    view.setBackgroundTintList(
                            ColorStateList.valueOf((color & 0x00FFFFFF) | 0x22000000));
                    ((TextView) view).setTextColor(color);
                    return false;
                }
                if (view.getId() == R.id.v_stripe) {
                    // UI 美化：卡片左侧色条与分类颜色一致
                    view.setBackgroundTintList(
                            ColorStateList.valueOf(categoryColor(cursor.getString(columnIndex))));
                    return true;
                }
                return false;
            }
        });

        setListAdapter(mAdapter);
        registerForContextMenu(getListView());
    }

    private void refreshList() {
        if (mAdapter == null) {
            return;
        }
        Cursor newCursor = managedQuery(NotePad.Notes.CONTENT_URI, PROJECTION,
                buildSelection(), buildSelectionArgs(), getSortOrder());
        mAdapter.changeCursor(newCursor);
        mNotesCursor = newCursor;
    }

    protected Uri getCurrentNote() {
        return getIntent().getData();
    }
}
