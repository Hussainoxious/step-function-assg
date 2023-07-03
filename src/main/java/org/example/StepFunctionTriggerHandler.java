package org.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.*;

import java.util.ArrayList;
import java.util.List;

public class StepFunctionTriggerHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final SfnClient sfnClient;
    private final String stateMachineArn = "arn:aws:states:us-east-1:868515001568:stateMachine:MyStateMachine";
    private String executionArn; // Variable to store the executionArn from the POST request

    public StepFunctionTriggerHandler() {
        this.sfnClient = SfnClient.create();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        String httpMethod = input.getHttpMethod();

        if (httpMethod.equalsIgnoreCase("GET")) {
            if (executionArn == null) {
                APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
                response.setStatusCode(400);
                response.setBody("Missing executionArn");
                return response;
            }

            List<String> passedStates = getPassedStates(executionArn);
            String currentStateName = passedStates.get(passedStates.size() - 1);
            String executionStatus = getExecutionStatus(executionArn);
            String executionResult = getExecutionResult(executionArn);
            String executionError = getExecutionError(executionArn);

            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
            response.setStatusCode(200);
            if (executionError != null) {
                response.setBody("Execution Error: " + executionError +
                        "\n\nPassed States: " + String.join(", ", passedStates) +
                        "\nCurrent State: " + currentStateName +
                        "\nExecution Status: " + executionStatus +
                        "\nExecution Result: " + executionResult);
            } else {
                response.setBody("Passed States: " + String.join(", ", passedStates) +
                        "\nCurrent State: " + currentStateName +
                        "\nExecution Status: " + executionStatus +
                        "\nExecution Result: " + executionResult);
            }

            return response;
        } else if (httpMethod.equalsIgnoreCase("POST")) {
            String marks = input.getBody();

            // Construct the input JSON for the Step Function
            String executionInput = "{\"marks\": " + marks + "}";

            StartExecutionRequest startExecutionRequest = StartExecutionRequest.builder()
                    .stateMachineArn(stateMachineArn)
                    .input(executionInput)
                    .build();

            StartExecutionResponse startExecutionResponse = sfnClient.startExecution(startExecutionRequest);
            executionArn = startExecutionResponse.executionArn();

            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
            response.setStatusCode(200);
            response.setBody(executionArn);

            return response;
        } else {
            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
            response.setStatusCode(400);
            response.setBody("Invalid HTTP method");

            return response;
        }
    }

    private List<String> getPassedStates(String executionArn) {
        GetExecutionHistoryRequest getExecutionHistoryRequest = GetExecutionHistoryRequest.builder()
                .executionArn(executionArn)
                .build();

        GetExecutionHistoryResponse getExecutionHistoryResponse = sfnClient.getExecutionHistory(getExecutionHistoryRequest);
        List<HistoryEvent> events = getExecutionHistoryResponse.events();

        List<String> passedStates = new ArrayList<>();

        for (HistoryEvent event : events) {
            String eventType = event.type().toString();

            if (eventType.equals("ExecutionStarted")) {
                passedStates.add("ExecutionStarted");
            } else if (eventType.equals("ExecutionSucceeded")) {
                passedStates.add("ExecutionSucceeded");
                break;
            } else if (eventType.equals("ExecutionFailed")) {
                passedStates.add("ExecutionFailed");
                break;
            } else if (event.stateEnteredEventDetails() != null) {
                passedStates.add(event.stateEnteredEventDetails().name());
            }
        }

        return passedStates;
    }

    private String getExecutionStatus(String executionArn) {
        DescribeExecutionRequest describeExecutionRequest = DescribeExecutionRequest.builder()
                .executionArn(executionArn)
                .build();

        DescribeExecutionResponse describeExecutionResponse = sfnClient.describeExecution(describeExecutionRequest);
        String executionStatus = describeExecutionResponse.status().toString();

        return executionStatus;
    }

    private String getExecutionResult(String executionArn) {
        DescribeExecutionRequest describeExecutionRequest = DescribeExecutionRequest.builder()
                .executionArn(executionArn)
                .build();

        DescribeExecutionResponse describeExecutionResponse = sfnClient.describeExecution(describeExecutionRequest);
        String executionResult = describeExecutionResponse.output();

        return executionResult;
    }

    private String getExecutionError(String executionArn) {
        GetExecutionHistoryRequest getExecutionHistoryRequest = GetExecutionHistoryRequest.builder()
                .executionArn(executionArn)
                .build();

        GetExecutionHistoryResponse getExecutionHistoryResponse = sfnClient.getExecutionHistory(getExecutionHistoryRequest);
        List<HistoryEvent> events = getExecutionHistoryResponse.events();

        for (HistoryEvent event : events) {
            String eventType = event.type().toString();

            if (eventType.equals("ExecutionFailed")) {
                return event.executionFailedEventDetails().cause();
            }
        }

        return null;
    }
}
