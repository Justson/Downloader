/*
 * Copyright (C)  Justson(https://github.com/Justson/Downloader)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.downloader.library;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author cenxiaozhong
 * @date 19-2-12
 * @since 1.0.0
 */
public class Rumtime {

    private static final Rumtime sInstance = new Rumtime();
    private DownloadTask sDefaultDownloadTask;
    private AtomicInteger mIDGenerator;
    private AtomicInteger mThreadGlobalCounter;
    private File mDownloadDir = null;
    private static Pattern DISPOSITION_PATTERN = Pattern.compile(".*filename=(.*)");
    static final String PREFIX = "Download-";
    boolean DEBUG = true;
    private String authority;

    public void setDebug(boolean debug) {
        this.DEBUG = debug;
    }

    public boolean isDebug() {
        return DEBUG;
    }

    private Rumtime() {
        mIDGenerator = new AtomicInteger(1);
        mThreadGlobalCounter = new AtomicInteger(1);
    }

    public static Rumtime getInstance() {
        return sInstance;
    }

    public DownloadTask getDefaultDownloadTask() {
        if (sDefaultDownloadTask == null) {
            createDefaultDownloadTask();
        }
        return sDefaultDownloadTask.clone();
    }

    private void createDefaultDownloadTask() {
        sDefaultDownloadTask = new DownloadTask();
        sDefaultDownloadTask.setBreakPointDownload(true)
                .setIcon(R.drawable.ic_file_download_black_24dp)
                .setConnectTimeOut(6000)
                .setBlockMaxTime(10 * 60 * 1000)
                .setDownloadTimeOut(Long.MAX_VALUE)
                .setParallelDownload(true)
                .setEnableIndicator(false)
                .setAutoOpen(false)
                .setForceDownload(true);
    }

    public String getIdentify() {
        return "Downloader";
    }

    public String getVersion() {
        return "4.0.3";
    }

    public int generateGlobalId() {
        return mIDGenerator.getAndIncrement();
    }

    public int generateGlobalThreadId() {
        return mThreadGlobalCounter.getAndIncrement();
    }

    public File createFile(Context context, Extra extra) {
        return createFile(context, extra, null);
    }

    public File createFile(Context context, Extra extra, File dir) {
        String fileName = "";
        try {
            fileName = getFileNameByContentDisposition(extra.getContentDisposition());
            if (TextUtils.isEmpty(fileName) && !TextUtils.isEmpty(extra.getUrl())) {
                Uri mUri = Uri.parse(extra.getUrl());
                fileName = mUri.getPath().substring(mUri.getPath().lastIndexOf('/') + 1);
            }
            if (!TextUtils.isEmpty(fileName) && fileName.length() > 64) {
                fileName = fileName.substring(fileName.length() - 64, fileName.length());
            }
            if (TextUtils.isEmpty(fileName)) {
                fileName = md5(extra.getUrl());
            }
            if (fileName.contains("\"")) {
                fileName = fileName.replace("\"", "");
            }
            String path = (dir == null || !dir.isDirectory()) ? getDir(context, extra.isEnableIndicator()).getPath() : dir.getAbsolutePath();
            File pathFile = new File(path);
            if (!pathFile.exists()) {
                pathFile.mkdirs();
            }
            return createFileByName(pathFile, context, fileName, !extra.isBreakPointDownload());
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean checkWifi(Context context) {
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity == null) {
            return false;
        }
        @SuppressLint("MissingPermission") NetworkInfo info = connectivity.getActiveNetworkInfo();
        return info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI;
    }

    public boolean checkNetwork(Context context) {
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity == null) {
            return false;
        }
        @SuppressLint("MissingPermission") NetworkInfo info = connectivity.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    File createFileByName(File path, Context context, String name, boolean cover) throws IOException {
        if (!path.isDirectory()) {
            return null;
        }
        File mFile = new File(path, name);
        if (mFile.exists()) {
            if (cover) {
                mFile.delete();
                mFile.createNewFile();
            }
        } else {
            mFile.createNewFile();
        }
        return mFile;
    }

    private String getFileNameByContentDisposition(String contentDisposition) {
        if (TextUtils.isEmpty(contentDisposition)) {
            return "";
        }
        Matcher m = DISPOSITION_PATTERN.matcher(contentDisposition.toLowerCase());
        if (m.find()) {
            return m.group(1);
        } else {
            return "";
        }
    }

    public File getDir(Context context, boolean isPublic) {
        File file = (mDownloadDir == null || !mDownloadDir.isDirectory()) ? context.getCacheDir() : mDownloadDir;
        file = new File(file, "download" + File.separator + (isPublic ? "public" : "privite"));
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    public File getDir(Context context) {
        return getDir(context, false);
    }

    public File getDefaultDir(Context context) {
        File file = new File(context.getCacheDir(), "download");
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    public void log(String tag, String msg) {
        if (DEBUG) {
            Log.i(tag, msg);
        }
    }

    public File uniqueFile(@NonNull DownloadTask downloadTask, @Nullable File targetDir) {
        String md5 = Rumtime.getInstance().md5(downloadTask.getUrl());
        File dir = (targetDir == null || !targetDir.isDirectory()) ? Rumtime.getInstance().getDir(downloadTask.getContext(), downloadTask.isEnableIndicator()) : targetDir;
        File target = new File(dir, md5);
        if (!target.exists()) {
            target.mkdirs();
        }
        if (!target.isDirectory()) {
            target.delete();
            target.mkdirs();
        }
        return createFile(downloadTask.getContext(), downloadTask, target);
    }

    public void setDownloadDir(File downloadDir, String authority) {
        mDownloadDir = downloadDir;
        this.authority = authority;
    }

    public void logError(String tag, String msg) {
        if (DEBUG) {
            Log.i(tag, msg);
        }
    }

    public String md5(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(str.getBytes());
            return new BigInteger(1, md.digest()).toString(16);
        } catch (Exception e) {
            if (isDebug()) {
                e.printStackTrace();
            }
        }
        return "";
    }

    public String getApplicationName(Context context) {
        PackageManager packageManager = null;
        ApplicationInfo applicationInfo = null;
        try {
            packageManager = context.getApplicationContext().getPackageManager();
            applicationInfo = packageManager.getApplicationInfo(context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            applicationInfo = null;
        }
        String applicationName =
                (String) packageManager.getApplicationLabel(applicationInfo);
        return applicationName;
    }

    public Intent getCommonFileIntentCompat(Context context, DownloadTask downloadTask) {
        Intent mIntent = new Intent().setAction(Intent.ACTION_VIEW);
        setIntentDataAndType(context, mIntent, getMIMEType(downloadTask.getFile()), downloadTask.getFile(), false, downloadTask.isCustomFile() ? downloadTask.getAuthority() : getAuthority(downloadTask.getContext()));
        return mIntent;
    }

    private String getAuthority(Context context) {
        return TextUtils.isEmpty(this.authority) ? (this.authority = context.getPackageName() + ".DownloadFileProvider") : this.authority;
    }

    public Uri getUriFromFile(Context context, File file, String authority) {
        Uri uri = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            uri = FileProvider.getUriForFile(context, authority, file); //getUriFromFileForN(context, file);
        } else {
            uri = Uri.fromFile(file);
        }
        return uri;
    }

    public Uri getUriFromFileForN(Context context, File file) {
        Uri fileUri = FileProvider.getUriForFile(context, context.getPackageName() + ".DownloadFileProvider", file);
        return fileUri;
    }


    public void setIntentDataAndType(Context context,
                                     Intent intent,
                                     String type,
                                     File file,
                                     boolean writeAble, String authority) {
        if (Build.VERSION.SDK_INT >= 24) {
            intent.setDataAndType(getUriFromFile(context, file, authority), type);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (writeAble) {
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }
        } else {
            intent.setDataAndType(Uri.fromFile(file), type);
        }
    }

    public String getMIMEType(File f) {
        String type = "";
        String fName = f.getName();
        String end = fName.substring(fName.lastIndexOf(".") + 1, fName.length()).toLowerCase();
        if (end.equals("pdf")) {
            type = "application/pdf";//
        } else if (end.equals("m4a") || end.equals("mp3") || end.equals("mid") ||
                end.equals("xmf") || end.equals("ogg") || end.equals("wav")) {
            type = "audio/*";
        } else if (end.equals("3gp") || end.equals("mp4")) {
            type = "video/*";
        } else if (end.equals("jpg") || end.equals("gif") || end.equals("png") ||
                end.equals("jpeg") || end.equals("bmp")) {
            type = "image/*";
        } else if (end.equals("apk")) {
            type = "application/vnd.android.package-archive";
        } else if (end.equals("pptx") || end.equals("ppt")) {
            type = "application/vnd.ms-powerpoint";
        } else if (end.equals("docx") || end.equals("doc")) {
            type = "application/vnd.ms-word";
        } else if (end.equals("xlsx") || end.equals("xls")) {
            type = "application/vnd.ms-excel";
        } else {
            type = "*/*";
        }
        return type;
    }
}
