package com.muddzdev.pixelshot;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import androidx.core.content.ContextCompat;

import java.io.File;

class Utils {

    public static String getMimeType(String src) {
        src = src.substring(1);
        if (src.equals("jpg")) {
            src = "jpeg";
        }
        return "image" + File.separator + src;
    }

    public static boolean isAndroidQ() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    public static boolean isStorageAvailable() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    public static boolean isPermissionGranted(Context context) {
        String permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }
}

