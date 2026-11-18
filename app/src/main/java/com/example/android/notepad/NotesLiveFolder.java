/*
 * Copyright (C) 2009 The Android Open Source Project
 */
package com.example.android.notepad;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.LiveFolders;

/**
 * 官方 Live Folder 入口，保留原始实现以避免破坏 manifest 的 intent-filter。
 * 现代 Android 上 Live Folder 机制已废弃，该 Activity 基本不会被触发。
 */
public class NotesLiveFolder extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        final String action = intent.getAction();
        if (LiveFolders.ACTION_CREATE_LIVE_FOLDER.equals(action)) {
            final Intent liveFolderIntent = new Intent();
            liveFolderIntent.setData(NotePad.Notes.LIVE_FOLDER_URI);
            liveFolderIntent.putExtra(LiveFolders.EXTRA_LIVE_FOLDER_NAME,
                    getString(R.string.live_folder_name));
            Intent.ShortcutIconResource foldericon =
                Intent.ShortcutIconResource.fromContext(this, android.R.drawable.ic_menu_agenda);
            liveFolderIntent.putExtra(LiveFolders.EXTRA_LIVE_FOLDER_ICON, foldericon);
            liveFolderIntent.putExtra(
                    LiveFolders.EXTRA_LIVE_FOLDER_DISPLAY_MODE,
                    LiveFolders.DISPLAY_MODE_LIST);
            Intent returnIntent =
                    new Intent(Intent.ACTION_EDIT, NotePad.Notes.CONTENT_ID_URI_PATTERN);
            liveFolderIntent.putExtra(LiveFolders.EXTRA_LIVE_FOLDER_BASE_INTENT, returnIntent);
            setResult(RESULT_OK, liveFolderIntent);
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }
}
