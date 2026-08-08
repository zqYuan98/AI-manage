package com.ailab.system.report;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Executed by ProcessBuilder in tests; it never invokes a shell or LibreOffice. */
public final class FakeLibreOfficeMain {
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "child".equals(args[0])) { Thread.sleep(30000L); return; }
        String input = args[args.length - 1]; if (input.contains("timeout")) { Thread.sleep(5000L); return; }
        if (input.contains("tree")) { Process child = new ProcessBuilder(System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator + "java.exe", "-cp", System.getProperty("java.class.path"), FakeLibreOfficeMain.class.getName(), "child").start(); long pid = ((Long) Process.class.getMethod("pid").invoke(child)).longValue(); Files.write(java.nio.file.Paths.get(input).getParent().getParent().resolve("child.pid"), String.valueOf(pid).getBytes(StandardCharsets.US_ASCII)); Thread.sleep(30000L); return; }
        if (input.contains("nonzero")) { for (int i=0;i<20000;i++) System.err.print('x'); System.exit(7); return; }
        if (input.contains("missing")) return;
        Path out = null; for (int i = 0; i < args.length - 1; i++) if ("--outdir".equals(args[i])) out = java.nio.file.Paths.get(args[i + 1]);
        String file = java.nio.file.Paths.get(input).getFileName().toString().replaceFirst("\\.docx$", ".pdf"); Files.write(out.resolve(file), ("%PDF-1.4\n" + java.util.Arrays.toString(args) + "\n%%EOF").getBytes(StandardCharsets.US_ASCII));
    }
}
