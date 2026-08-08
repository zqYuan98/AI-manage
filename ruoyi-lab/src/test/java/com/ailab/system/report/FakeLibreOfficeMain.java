package com.ailab.system.report;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Executed by ProcessBuilder in tests; it never invokes a shell or LibreOffice. */
public final class FakeLibreOfficeMain {
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "child".equals(args[0])) { Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { Thread.sleep(30000L); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } })); Thread.sleep(30000L); return; }
        String input = args[args.length - 1]; if (input.contains("timeout")) { Thread.sleep(5000L); return; }
        if (input.contains("tree") || input.contains("orphan")) { String executable = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java"; Process child = new ProcessBuilder(java.nio.file.Paths.get(System.getProperty("java.home"), "bin", executable).toString(), "-cp", System.getProperty("java.class.path"), FakeLibreOfficeMain.class.getName(), "child").start(); long pid = ((Long) Process.class.getMethod("pid").invoke(child)).longValue(); Files.write(java.nio.file.Paths.get(input).getParent().getParent().resolve("child.pid"), String.valueOf(pid).getBytes(StandardCharsets.US_ASCII)); Thread.sleep(input.contains("orphan") ? 400L : 30000L); return; }
        if (input.contains("nonzero")) { for (int i=0;i<80000;i++) System.err.print('x'); System.exit(7); return; }
        if (input.contains("missing")) return;
        Path out = null; for (int i = 0; i < args.length - 1; i++) if ("--outdir".equals(args[i])) out = java.nio.file.Paths.get(args[i + 1]);
        String file = java.nio.file.Paths.get(input).getFileName().toString().replaceFirst("\\.docx$", ".pdf"); if (input.contains("invalid")) { Files.write(out.resolve(file), "not-a-pdf".getBytes(StandardCharsets.US_ASCII)); return; } if (input.contains("oversize")) { Files.write(out.resolve(file), new byte[2048]); return; } Files.write(out.resolve(file), ("%PDF-1.4\n" + java.util.Arrays.toString(args) + "\n%%EOF").getBytes(StandardCharsets.US_ASCII));
    }
}
