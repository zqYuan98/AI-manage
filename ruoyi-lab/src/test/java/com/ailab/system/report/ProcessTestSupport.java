package com.ailab.system.report;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

/** Java-8-compatible process assertions shared by the report exporter tests and fake. */
public final class ProcessTestSupport {
    private ProcessTestSupport() { }

    public static int currentPid() {
        return new SystemInfo().getOperatingSystem().getProcessId();
    }

    public static void writeCurrentPid(Path target) throws IOException {
        Files.write(target, String.valueOf(currentPid()).getBytes(StandardCharsets.US_ASCII));
    }

    public static long readPid(Path target) throws IOException {
        return Long.parseLong(new String(Files.readAllBytes(target), StandardCharsets.US_ASCII));
    }

    public static boolean isAlive(long pid) {
        if (pid <= 0L || pid > Integer.MAX_VALUE) return false;
        int value = (int) pid;
        if (windows()) {
            WinNT.HANDLE handle = Kernel32.INSTANCE.OpenProcess(0x1000, false, value);
            if (handle == null || WinBase.INVALID_HANDLE_VALUE.equals(handle)) {
                int error = Kernel32.INSTANCE.GetLastError();
                if (error == 87) return false;
                throw new IllegalStateException("cannot inspect process " + value + ", error=" + error);
            }
            try {
                IntByReference code = new IntByReference();
                return Kernel32.INSTANCE.GetExitCodeProcess(handle, code) && code.getValue() == 259;
            } finally {
                Kernel32.INSTANCE.CloseHandle(handle);
            }
        }
        OperatingSystem os = new SystemInfo().getOperatingSystem();
        OSProcess process = os.getProcess(value);
        return process != null && process.updateAttributes() && process.getState() != OSProcess.State.INVALID;
    }

    public static void awaitDead(long pid, long timeoutSeconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (isAlive(pid) && System.nanoTime() < deadline) Thread.sleep(20L);
        if (isAlive(pid)) throw new AssertionError("process is still alive: " + pid);
    }

    public static void awaitFile(Path target, long timeoutSeconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (!Files.isRegularFile(target) && System.nanoTime() < deadline) Thread.sleep(20L);
        if (!Files.isRegularFile(target)) throw new AssertionError("file was not created: " + target);
    }

    public static String javaExecutable() {
        return java.nio.file.Paths.get(System.getProperty("java.home"), "bin", windows() ? "java.exe" : "java").toString();
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
