package com.lambda01;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.io.PrintWriter;
import java.io.StringWriter;

public class CodeExecutionLambdaHandler implements RequestHandler<CodeExecutionRequest, CodeExecutionResponse> {

    private final CodeExecutionService codeExecutionService = new CodeExecutionService();

    @Override
    public CodeExecutionResponse handleRequest(CodeExecutionRequest request, Context context) {
        context.getLogger().log("Received request: " + request.getJavaCode());

        CodeExecutionResponse response = new CodeExecutionResponse();
        try {
            response = codeExecutionService.executeJavaCode(request);
        } catch (Exception e) {
            context.getLogger().log("Error executing Java code: " + e.getMessage());
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            response.setErrors("Internal server error: " + sw.toString());
        }
        return response;
    }
}
