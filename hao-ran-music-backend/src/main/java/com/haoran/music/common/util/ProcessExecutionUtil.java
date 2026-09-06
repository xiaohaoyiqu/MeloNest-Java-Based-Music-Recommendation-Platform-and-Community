


package com.haoran.music.common.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;








public final class ProcessExecutionUtil {

    private static final int DEFAULT_MAX_OUTPUT_CHARS = 64 * 1024;

    private ProcessExecutionUtil() {
    }

    public static Result execute(List<String> command, long timeoutSeconds)
            throws IOException, InterruptedException {
        return execute(command, timeoutSeconds, DEFAULT_MAX_OUTPUT_CHARS);
    }

    public static Result execute(List<String> command, long timeoutSeconds, int maxOutputChars)
            throws IOException, InterruptedException {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        StringBuilder output = new StringBuilder(Math.min(Math.max(maxOutputChars, 1024), DEFAULT_MAX_OUTPUT_CHARS));
        Thread outputReader = new Thread(() -> readOutput(process, output, maxOutputChars),
                "process-output-reader");
        outputReader.setDaemon(true);
        outputReader.start();

        boolean finished;
        try {
            finished = process.waitFor(Math.max(1L, timeoutSeconds), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            destroy(process);
            outputReader.interrupt();
            throw e;
        }

        if (!finished) {
            destroy(process);
            outputReader.interrupt();
            outputReader.join(1000L);
            return new Result(false, true, -1, output.toString());
        }

        outputReader.join(1000L);
        return new Result(true, false, process.exitValue(), output.toString());
    }

    private static void readOutput(Process process, StringBuilder output, int maxOutputChars) {
        int limit = Math.max(1024, maxOutputChars);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                if (output.length() < limit) {
                    int remaining = limit - output.length();
                    output.append(buffer, 0, Math.min(read, remaining));
                }
            }
        } catch (IOException ignored) {

        }
    }

    private static void destroy(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(1L, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    public static final class Result {
        private final boolean finished;
        private final boolean timedOut;
        private final int exitCode;
        private final String output;

        private Result(boolean finished, boolean timedOut, int exitCode, String output) {
            this.finished = finished;
            this.timedOut = timedOut;
            this.exitCode = exitCode;
            this.output = output;
        }

        public boolean isFinished() {
            return finished;
        }

        public boolean isTimedOut() {
            return timedOut;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getOutput() {
            return output;
        }
    }
}

