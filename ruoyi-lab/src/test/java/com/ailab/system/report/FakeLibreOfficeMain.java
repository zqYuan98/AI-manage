package com.ailab.system.report;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/** Executed by ProcessBuilder in tests; it never invokes a shell or LibreOffice. */
public final class FakeLibreOfficeMain {
    private static final long SLEEP_MILLIS = 30000L;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "sleeper".equals(args[0])) {
            Thread.sleep(SLEEP_MILLIS);
            return;
        }
        if (args.length > 0 && "grandchild".equals(args[0])) {
            Path base = Paths.get(args[1]);
            String tag = args[2];
            ProcessTestSupport.writeCurrentPid(base.resolve(tag + ".grandchild.pid"));
            Thread.sleep(SLEEP_MILLIS);
            return;
        }
        if (args.length > 0 && "child".equals(args[0])) {
            Path base = Paths.get(args[1]);
            String tag = args[2];
            ProcessTestSupport.writeCurrentPid(base.resolve(tag + ".child.pid"));
            ProcessBuilder grandchild = javaProcess("grandchild", base.toString(), tag);
            if (args.length > 3) grandchild.command().add(args[3]);
            grandchild.start();
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override public void run() {
                    try { Thread.sleep(5000L); }
                    catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                }
            }));
            Thread.sleep(SLEEP_MILLIS);
            return;
        }
        if (args.length > 0 && "late-child".equals(args[0])) {
            Path base = Paths.get(args[1]);
            String tag = args[2];
            ProcessTestSupport.writeCurrentPid(base.resolve(tag + ".late.pid"));
            Thread.sleep(SLEEP_MILLIS);
            return;
        }
        if (args.length > 0 && "late-broker".equals(args[0])) {
            lateBroker(Paths.get(args[1]), args[2]);
            return;
        }

        String input = args[args.length - 1];
        Path inputPath = Paths.get(input);
        Path base = inputPath.getParent().getParent();
        String tag = inputPath.getFileName().toString().replaceFirst("\\.docx$", "");
        if (input.contains("timeout")) {
            Thread.sleep(5000L);
            return;
        }
        if (input.contains("snapshotfail")) {
            ProcessTestSupport.writeCurrentPid(base.resolve("root.pid"));
            Thread.sleep(SLEEP_MILLIS);
            return;
        }
        if (treeMode(tag)) {
            String token = token(args);
            ProcessTestSupport.writeCurrentPid(base.resolve(tag + ".root.pid"));
            Files.write(base.resolve(tag + ".token"), token.getBytes(StandardCharsets.UTF_8));
            if (tag.startsWith("barrier-")) barrier(base, tag);
            if (!tag.contains("late-tree")) {
                ProcessBuilder child = javaProcess("child", base.toString(), tag);
                if (tag.contains("orphan")) child.command().add(token);
                child.start();
                ProcessTestSupport.awaitFile(base.resolve(tag + ".child.pid"), 20L);
                ProcessTestSupport.awaitFile(base.resolve(tag + ".grandchild.pid"), 20L);
            }
            Thread.sleep(tag.contains("orphan") ? 400L : SLEEP_MILLIS);
            return;
        }
        if (input.contains("nonzero")) {
            char[] chunk = new char[80000];
            Arrays.fill(chunk, 'x');
            System.err.print(new String(chunk));
            System.exit(7);
            return;
        }
        if (input.contains("missing")) return;
        Path out = null;
        for (int i = 0; i < args.length - 1; i++) if ("--outdir".equals(args[i])) out = Paths.get(args[i + 1]);
        String file = inputPath.getFileName().toString().replaceFirst("\\.docx$", ".pdf");
        if (input.contains("invalid")) {
            Files.write(out.resolve(file), "not-a-pdf".getBytes(StandardCharsets.US_ASCII));
            return;
        }
        if (input.contains("oversize")) {
            byte[] value = new byte[2048];
            value[0] = '%'; value[1] = 'P'; value[2] = 'D'; value[3] = 'F'; value[4] = '-';
            Files.write(out.resolve(file), value);
            return;
        }
        Files.write(out.resolve(file), ("%PDF-1.4\n" + String.join("|", args)).getBytes(StandardCharsets.UTF_8));
    }

    private static void lateBroker(Path base, String tag) throws Exception {
        Path rootPidFile = base.resolve(tag + ".root.pid");
        Path tokenFile = base.resolve(tag + ".token");
        ProcessTestSupport.awaitFile(rootPidFile, 10L);
        ProcessTestSupport.awaitFile(tokenFile, 10L);
        long rootPid = ProcessTestSupport.readPid(rootPidFile);
        ProcessTestSupport.awaitDead(rootPid, 10L);
        String token = new String(Files.readAllBytes(tokenFile), StandardCharsets.UTF_8);
        javaProcess("late-child", base.toString(), tag, token).start();
    }

    private static void barrier(Path base, String tag) throws Exception {
        Files.write(base.resolve(tag + ".ready"), new byte[] {1});
        String peer = tag.startsWith("barrier-a-") ? tag.replaceFirst("barrier-a-", "barrier-b-")
                : tag.replaceFirst("barrier-b-", "barrier-a-");
        ProcessTestSupport.awaitFile(base.resolve(peer + ".ready"), 10L);
    }

    private static boolean treeMode(String tag) {
        return tag.contains("tree") || tag.contains("orphan");
    }

    private static String token(String[] args) {
        for (String arg : args) if (arg.startsWith("-env:UserInstallation=")) return arg;
        return "";
    }

    private static ProcessBuilder javaProcess(String... args) {
        java.util.List<String> command = new java.util.ArrayList<String>();
        command.add(ProcessTestSupport.javaExecutable());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(FakeLibreOfficeMain.class.getName());
        command.addAll(Arrays.asList(args));
        return new ProcessBuilder(command);
    }
}
