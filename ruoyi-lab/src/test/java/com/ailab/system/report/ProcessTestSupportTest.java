package com.ailab.system.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ProcessTestSupportTest {
    @Test
    void writeCurrentPidPublishesThroughATemporarySibling() throws Exception {
        Path directory = Files.createTempDirectory("pid publish ");
        Path target = directory.resolve("child.pid");
        Files.write(target, "invalid".getBytes(StandardCharsets.US_ASCII));
        WatchService watch = directory.getFileSystem().newWatchService();
        directory.register(watch, StandardWatchEventKinds.ENTRY_CREATE);
        try {
            ProcessTestSupport.writeCurrentPid(target);

            boolean temporarySiblingCreated = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
            while (!temporarySiblingCreated && System.nanoTime() < deadline) {
                WatchKey key = watch.poll(100L, TimeUnit.MILLISECONDS);
                if (key == null) continue;
                for (WatchEvent<?> event : key.pollEvents()) {
                    String name = String.valueOf(event.context());
                    if (name.startsWith("child.pid.") && name.endsWith(".tmp")) {
                        temporarySiblingCreated = true;
                    }
                }
                key.reset();
            }
            assertTrue(temporarySiblingCreated, "PID must be staged in a temporary sibling");
            assertEquals(ProcessTestSupport.currentPid(), ProcessTestSupport.awaitPid(target, 1L));
        } finally {
            watch.close();
        }
    }

    @Test
    void awaitPidKeepsWaitingThroughEmptyPartialAndNonPositiveValues() throws Exception {
        Path target = Files.createTempDirectory("pid await ").resolve("child.pid");
        Files.write(target, new byte[0]);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> writer = executor.submit(() -> {
                try {
                    Thread.sleep(60L);
                    Files.write(target, "-".getBytes(StandardCharsets.US_ASCII));
                    Thread.sleep(60L);
                    Files.write(target, "not-a-number".getBytes(StandardCharsets.US_ASCII));
                    Thread.sleep(60L);
                    Files.write(target, "0".getBytes(StandardCharsets.US_ASCII));
                    Thread.sleep(60L);
                    Path complete = target.resolveSibling("complete.tmp");
                    Files.write(complete, "321\n".getBytes(StandardCharsets.US_ASCII));
                    Files.move(complete, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });

            assertEquals(321L, ProcessTestSupport.awaitPid(target, 5L));
            writer.get(5L, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void awaitPidTimeoutReportsTheFileAndLastObservedValue() throws Exception {
        Path target = Files.createTempDirectory("pid timeout ").resolve("child.pid");
        Files.write(target, "partial-".getBytes(StandardCharsets.US_ASCII));

        AssertionError failure = assertThrows(AssertionError.class,
                () -> ProcessTestSupport.awaitPid(target, 0L));

        assertTrue(failure.getMessage().contains(target.toString()));
        assertTrue(failure.getMessage().contains("partial-"));
    }
}
