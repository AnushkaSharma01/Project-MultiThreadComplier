package com.lambda01;

import java.io.*;
import java.util.concurrent.*;

public class CodeExecutionService {

    private final ExecutorService executorService = Executors.newFixedThreadPool(3);

    public CodeExecutionResponse executeJavaCode(CodeExecutionRequest request) {
        CodeExecutionResponse response = new CodeExecutionResponse();
        try {
            response = executeCode(request);
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            response.setErrors(sw.toString());
        }
        return response;
    }

    private CodeExecutionResponse executeCode(CodeExecutionRequest request) throws Exception {
        CodeExecutionResponse response = new CodeExecutionResponse();

        String className = extractClassName(request.getJavaCode());
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        File javaFile = new File(tempDir, className + ".java");

        // Write Java code to file
        try (FileWriter writer = new FileWriter(javaFile)) {
            writer.write(request.getJavaCode());
        } catch (IOException e) {
            throw new RuntimeException("Error writing Java code to file: " + e.getMessage());
        }

        // Compile Java code
        compileJavaCode(javaFile);

        // Execute compiled Java class
        executeCompiledJavaClass(className, tempDir, request.getUserInput(), response);

        return response;
    }

    private void compileJavaCode(File javaFile) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder("javac", javaFile.getAbsolutePath());
        processBuilder.directory(javaFile.getParentFile());

        Process compileProcess = processBuilder.start();
        int exitCode = compileProcess.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Compilation failed. Error: " + readStream(compileProcess.getErrorStream()));
        }
    }

    private void executeCompiledJavaClass(String className, File tempDir, String userInput, CodeExecutionResponse response) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder("java", "-cp", tempDir.getAbsolutePath(), className);
        processBuilder.directory(tempDir);

        Process runProcess = processBuilder.start();

        // Redirect System.in to provide input to the program (if needed)
        OutputStream stdin = runProcess.getOutputStream();
        if (userInput != null && !userInput.isEmpty()) {
            String[] inputs = userInput.split(" "); // Split inputs by space or other delimiter
            for (String input : inputs) {
                stdin.write((input + "\n").getBytes());
                stdin.flush();
            }
        }

        // Capture output and errors
        String output = readStream(runProcess.getInputStream());
        String errors = readStream(runProcess.getErrorStream());

        // Set output and errors in response
        response.setOutput(output);
        response.setErrors(errors);

        // Check for infinite loop (add timeout handling)
        if (!waitForProcess(runProcess, 10)) {
            runProcess.destroy();
            throw new RuntimeException("Execution timeout or interrupted.");
        }
    }

    private boolean waitForProcess(Process process, int timeoutInSeconds) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (isAlive(process)) {
            if (System.currentTimeMillis() - startTime > timeoutInSeconds * 1000) {
                return false; // Timeout occurred
            }
            Thread.sleep(100);
        }
        return true; // Process completed within timeout
    }

    private boolean isAlive(Process process) {
        try {
            process.exitValue();
            return false; // Process has terminated
        } catch (IllegalThreadStateException e) {
            return true; // Process is still running
        }
    }

    private String readStream(InputStream stream) throws IOException {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }
        }
        return result.toString();
    }

    // Utility method to extract class name from Java code
    private String extractClassName(String javaCode) {
        int classIndex = javaCode.indexOf("public class");
        if (classIndex == -1) {
            throw new IllegalArgumentException("Java code must contain a public class declaration.");
        }
        int startIndex = classIndex + "public class".length();
        int endIndex = javaCode.indexOf("{", startIndex);
        return javaCode.substring(startIndex, endIndex).trim();
    }
}
