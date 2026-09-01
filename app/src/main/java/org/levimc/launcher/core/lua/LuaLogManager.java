package org.levimc.launcher.core.lua;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.FileProvider;

import org.levimc.launcher.util.LauncherStorage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class LuaLogManager {
    private static final Object LOCK = new Object();
    private static final long MAX_LOG_BYTES = 512L * 1024L;
    private static final long MAX_CRASH_EXPORT_BYTES = 768L * 1024L;
    private static File logFile;

    private LuaLogManager() {
    }

    public static void initialize(Context context) {
        synchronized (LOCK) {
            File logDir = new File(context.getFilesDir(), "lua-client/logs");
            if (!logDir.exists()) logDir.mkdirs();
            logFile = new File(logDir, "lua-client.log");
            rotateIfNeeded();
        }
        record("launcher", "Inicializacao do Lua Client Mobile");
        record("device", "Android " + Build.VERSION.RELEASE + "; ABI="
                + (Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "desconhecida"));
    }

    public static void record(String area, String message) {
        synchronized (LOCK) {
            if (logFile == null) return;
            rotateIfNeeded();
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write(timestamp());
                writer.write(" [");
                writer.write(sanitize(area));
                writer.write("] ");
                writer.write(sanitize(message));
                writer.write('\n');
            } catch (Exception ignored) {
            }
        }
    }

    public static void share(Activity activity) {
        try {
            record("logs", "Exportacao solicitada pelo usuario");
            File shareDir = new File(activity.getCacheDir(), "lua_client_logs_share");
            if (!shareDir.exists()) shareDir.mkdirs();
            File exportFile = new File(shareDir, "LuaClient-Mobile-v0.1.2-log.txt");
            exportDiagnostics(activity, exportFile);

            Uri uri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    exportFile);
            Intent intent = new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, "Exportar logs do Lua Client"));
        } catch (Exception error) {
            record("logs", "Falha ao exportar: " + error.getClass().getSimpleName());
        }
    }

    private static void exportDiagnostics(Context context, File target) throws Exception {
        try (FileOutputStream out = new FileOutputStream(target)) {
            writeText(out, "Lua Client Mobile v0.1.2 - diagnostico local\n\n");
            writeText(out, "===== LOG DO LUA CLIENT =====\n");
            if (logFile == null || !logFile.isFile()) {
                writeText(out, "Nenhum log do Lua Client disponivel.\n");
            } else {
                appendTail(logFile, out, MAX_LOG_BYTES);
            }

            File nativeCrash = findLatestCrashLog(LauncherStorage.getCrashLogsDir(context));
            writeText(out, "\n===== ULTIMO RELATORIO NATIVO =====\n");
            if (nativeCrash == null) {
                writeText(out, "Nenhum tombstone nativo encontrado.\n");
            } else {
                writeText(out, "Arquivo: " + nativeCrash.getName() + "\n");
                appendTail(nativeCrash, out, MAX_CRASH_EXPORT_BYTES);
            }
        }
    }

    private static File findLatestCrashLog(File directory) {
        if (directory == null || !directory.isDirectory()) return null;
        File[] files = directory.listFiles(file -> file.isFile() && !file.getName().startsWith("."));
        if (files == null) return null;

        File latest = null;
        for (File file : files) {
            if (latest == null || file.lastModified() > latest.lastModified()) latest = file;
        }
        return latest;
    }

    private static void appendTail(File source, FileOutputStream out, long maxBytes) throws Exception {
        try (RandomAccessFile input = new RandomAccessFile(source, "r")) {
            long start = Math.max(0L, input.length() - maxBytes);
            input.seek(start);
            if (start > 0L) writeText(out, "[inicio omitido por limite de tamanho]\n");
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) > 0) out.write(buffer, 0, count);
        }
    }

    private static void writeText(FileOutputStream out, String value) throws Exception {
        out.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void rotateIfNeeded() {
        if (logFile == null || !logFile.isFile() || logFile.length() <= MAX_LOG_BYTES) return;
        File previous = new File(logFile.getParentFile(), "lua-client.previous.log");
        if (previous.exists()) previous.delete();
        logFile.renameTo(previous);
    }

    private static String timestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ').replace('\r', ' ');
    }
}
