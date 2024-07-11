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


        try (FileWriter writer = new FileWriter(javaFile)) {
            writer.write(request.getJavaCode());
        } catch (IOException e) {
            throw new RuntimeException("Error writing Java code to file: " + e.getMessage());
        }


        compileJavaCode(javaFile);


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

        OutputStream stdin = runProcess.getOutputStream();
        if (userInput != null && !userInput.isEmpty()) {
            String[] inputs = userInput.split(",");
            for (int i = 0; i < inputs.length; i++) {
                String input = inputs[i];
                stdin.write((input + "\n").getBytes());
                stdin.flush();
            }


        }


        String output = readStream(runProcess.getInputStream());
        String errors = readStream(runProcess.getErrorStream());


        response.setOutput(output);
        response.setErrors(errors);


        if (!waitForProcess(runProcess, 10)) {
            runProcess.destroy();
            throw new RuntimeException("Execution timeout or interrupted.");
        }
    }


    private boolean waitForProcess(Process process, int timeoutInSeconds) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (isAlive(process)) {
            if (System.currentTimeMillis() - startTime > timeoutInSeconds * 1000) {
                return false;
            }
            Thread.sleep(100);
        }
        return true;
    }

    private boolean isAlive(Process process) {
        try {
            process.exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return true;
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
