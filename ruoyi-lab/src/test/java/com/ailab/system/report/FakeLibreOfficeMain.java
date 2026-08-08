package com.ailab.system.report;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;

/** Executed by ProcessBuilder in tests; it never invokes a shell or LibreOffice. */
public final class FakeLibreOfficeMain {
    private static final long SLEEP_MILLIS = 30000L;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "sleeper".equals(args[0])) {
            Thread.sleep(SLEEP_MILLIS);
            return;
        }
        if (args.length > 1 && "pid-sleeper".equals(args[0])) {
            ProcessTestSupport.writeCurrentPid(Paths.get(args[1]));
            Thread.sleep(SLEEP_MILLIS);
            return;
        }
        if (args.length > 0 && "terminator-output".equals(args[0])) {
            byte[] chunk = new byte[4096];
            Arrays.fill(chunk, (byte) 'x');
            for (int i = 0; i < 128; i++) {
                System.out.write(chunk);
                System.err.write(chunk);
            }
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
                ProcessTestSupport.awaitPid(base.resolve(tag + ".child.pid"), 20L);
                ProcessTestSupport.awaitPid(base.resolve(tag + ".grandchild.pid"), 20L);
            }
            Thread.sleep(tag.contains("orphan") ? 400L : SLEEP_MILLIS);
            return;
        }
        // A real office process lives long enough to be bound to a native lease before producing output.
        Thread.sleep(700L);
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
        if (input.contains("truncated")) {
            Files.write(out.resolve(file), "%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\nstartxref\n9\n"
                    .getBytes(StandardCharsets.US_ASCII));
            return;
        }
        if (input.contains("bad-xref")) {
            Files.write(out.resolve(file), "%PDF-1.4\nstartxref\n999999\n%%EOF\n"
                    .getBytes(StandardCharsets.US_ASCII));
            return;
        }
        if (input.contains("oversize")) {
            byte[] value = new byte[2048];
            value[0] = '%'; value[1] = 'P'; value[2] = 'D'; value[3] = 'F'; value[4] = '-';
            Files.write(out.resolve(file), value);
            return;
        }
        Files.write(out.resolve(file), minimalPdf(String.join("|", args)));
    }

    private static byte[] minimalPdf(String payload) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        write(out, "%PDF-1.4\n%ARGS " + payload.replace('\n', ' ').replace('\r', ' ') + "\n");
        int catalog = out.size();
        write(out, "1 0 obj\n<</Type /Catalog /Pages 2 0 R>>\nendobj\n");
        int pages = out.size();
        write(out, "2 0 obj\n<</Type /Pages /Count 0 /Kids []>>\nendobj\n");
        int xref = out.size();
        write(out, "xref\n0 3\n0000000000 65535 f \n");
        write(out, String.format(Locale.ROOT, "%010d 00000 n \n", catalog));
        write(out, String.format(Locale.ROOT, "%010d 00000 n \n", pages));
        write(out, "trailer\n<</Size 3 /Root 1 0 R>>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static void write(java.io.ByteArrayOutputStream out, String value) throws java.io.IOException {
        out.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void lateBroker(Path base, String tag) throws Exception {
        ProcessTestSupport.ChildObserver childObserver = ProcessTestSupport.ownChildObserver();
        Path rootPidFile = base.resolve(tag + ".root.pid");
        Path tokenFile = base.resolve(tag + ".token");
        ProcessTestSupport.awaitFile(tokenFile, 10L);
        long rootPid = ProcessTestSupport.awaitPid(rootPidFile, 10L);
        ProcessTestSupport.awaitDead(rootPid, 10L);
        String token = new String(Files.readAllBytes(tokenFile), StandardCharsets.UTF_8);
        Process child = javaProcess("late-child", base.toString(), tag, token).start();
        childObserver.awaitAndPublish(child, base.resolve(tag + ".spawned"), 10L);
        if (!child.waitFor(10L, java.util.concurrent.TimeUnit.SECONDS)) {
            child.destroyForcibly();
            child.waitFor(5L, java.util.concurrent.TimeUnit.SECONDS);
            throw new AssertionError("runner did not terminate the broker-observed late child");
        }
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
