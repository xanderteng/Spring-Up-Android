package com.springup.app.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class AudioStorageHelper {

    private static final String TAG = "AudioStorageHelper";
    private static final String FOLDER_NAME = "custom_alarms";

    @Nullable
    public static String copyAudioToInternalStorage(Context context, Uri sourceUri) {
        if (sourceUri == null) return null;

        try {
            File internalDir = new File(context.getFilesDir(), FOLDER_NAME);
            if (!internalDir.exists()) {
                internalDir.mkdirs();
            }

            String originalName = getFileName(context, sourceUri);
            if (originalName == null || originalName.trim().isEmpty()) {
                originalName = "custom_tone_" + System.currentTimeMillis() + ".mp3";
            } else {
                // Sanitize file name
                originalName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
            }

            File destinationFile = new File(internalDir, originalName);

            try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
                 OutputStream out = new FileOutputStream(destinationFile)) {
                if (in == null) return null;

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }

            return Uri.fromFile(destinationFile).toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy audio to internal storage", e);
            return null;
        }
    }

    @Nullable
    public static String getFileName(Context context, Uri uri) {
        String result = null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        result = cursor.getString(index);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (result == null && uri.getPath() != null) {
            int cut = uri.getPath().lastIndexOf('/');
            if (cut != -1) {
                result = uri.getPath().substring(cut + 1);
            }
        }
        return result;
    }

    public static class SoundItem {
        private final String name;
        private final String uri;
        private final boolean isDefault;
        private final boolean isCustom;

        public SoundItem(String name, String uri, boolean isDefault, boolean isCustom) {
            this.name = name;
            this.uri = uri;
            this.isDefault = isDefault;
            this.isCustom = isCustom;
        }

        public String getName() { return name; }
        public String getUri() { return uri; }
        public boolean isDefault() { return isDefault; }
        public boolean isCustom() { return isCustom; }
    }

    public static java.util.List<SoundItem> getSavedSounds(Context context) {
        java.util.List<SoundItem> list = new java.util.ArrayList<>();

        // 1. Built-in default siren
        list.add(new SoundItem("Default Siren", null, true, false));

        // 2. Custom imported sounds in app's internal storage
        File internalDir = new File(context.getFilesDir(), FOLDER_NAME);
        if (internalDir.exists() && internalDir.isDirectory()) {
            File[] files = internalDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && (f.getName().endsWith(".mp3") || f.getName().endsWith(".wav") || f.getName().endsWith(".m4a") || f.getName().endsWith(".ogg"))) {
                        list.add(new SoundItem(f.getName(), Uri.fromFile(f).toString(), false, true));
                    }
                }
            }
        }

        // 3. System Alarm ringtones
        try {
            android.media.RingtoneManager rm = new android.media.RingtoneManager(context);
            rm.setType(android.media.RingtoneManager.TYPE_ALARM);
            Cursor cursor = rm.getCursor();
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String title = cursor.getString(android.media.RingtoneManager.TITLE_COLUMN_INDEX);
                    Uri ringtoneUri = rm.getRingtoneUri(cursor.getPosition());
                    if (ringtoneUri != null) {
                        list.add(new SoundItem(title, ringtoneUri.toString(), false, false));
                    }
                }
            }
        } catch (Exception ignored) {}

        return list;
    }

    public static boolean deleteCustomSound(Context context, String uriStr) {
        if (uriStr == null) return false;
        try {
            Uri uri = Uri.parse(uriStr);
            if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
                File file = new File(uri.getPath());
                return file.delete();
            }
        } catch (Exception ignored) {}
        return false;
    }
}