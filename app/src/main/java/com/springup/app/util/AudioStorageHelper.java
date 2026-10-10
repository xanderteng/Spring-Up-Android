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
}