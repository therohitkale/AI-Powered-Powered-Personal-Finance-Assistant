// Copyright (c) Microsoft. All rights reserved.
package com.microsoft.openai.samples.assistant.agent;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.aiservices.openai.chatcompletion.OpenAIChatCompletion;
import com.microsoft.semantickernel.orchestration.*;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;
import com.microsoft.semantickernel.services.chatcompletion.ChatHistory;
import org.json.JSONException;
import org.json.JSONObject;

public class IntentAgent {

    private OpenAIAsyncClient client;

    private Kernel kernel;

    private ChatCompletionService chat;

    private String INTENT_SYSTEM_MESSAGE = """
You are a personal financial advisor who help bank customers manage their banking accounts and services.
The user may need help with his recurrent bill payments, it may start the payment checking payments history for a specific payee.
In other cases it may want to just review account details or transactions history.
Based on the conversation you need to identify the user intent.
The available intents are:
"BillPayment","RepeatTransaction","TransactionHistory","AccountInfo"
If none of the intents are identified provide the user with the list of the available intents.

If an intent is identified return the output as json format as below
{
"intent": "BillPayment"
 }

If you don't understand or if an intent is not identified be polite with the user, ask clarifying question also using the list of the available intents. 
Don't add any comments in the output or other characters, just the use a json format.
            
    """;

    public IntentAgent(OpenAIAsyncClient client, String modelId){
        this.client = client;
        this.chat = OpenAIChatCompletion.builder()
                .withModelId(modelId)
                .withOpenAIAsyncClient(client)
                .build();

        kernel = Kernel.builder()
                .withAIService(ChatCompletionService.class, chat)
                .build();
    }
    public IntentAgent(String azureClientKey, String clientEndpoint, String modelId){
        this.client = new OpenAIClientBuilder()
                .credential(new AzureKeyCredential(azureClientKey))
                .endpoint(clientEndpoint)
                .httpLogOptions(new HttpLogOptions().setLogLevel(HttpLogDetailLevel.BODY_AND_HEADERS))
                .buildAsyncClient();

        this.chat = OpenAIChatCompletion.builder()
                .withModelId(modelId)
                .withOpenAIAsyncClient(client)
                .build();

        kernel = Kernel.builder()
                .withAIService(ChatCompletionService.class, chat)
                .build();
    }

    public IntentResponse run(ChatHistory userChatHistory,AgentContext agentContext){
        var agentChatHistory = new ChatHistory(INTENT_SYSTEM_MESSAGE);
        agentChatHistory.addAll(userChatHistory);

        var messages = chat.getChatMessageContentsAsync(
                        agentChatHistory,
                        kernel,
                        InvocationContext.builder().withPromptExecutionSettings(
                                PromptExecutionSettings.builder()
                                        .withTemperature(0.0)
                                        .withTopP(1)
                                        .withMaxTokens(200)
                                        .build())
                                .build())
                .block();

        var message = messages.get(0);

        JSONObject jsonData = new JSONObject();

        /**
         * Try to see if the model answered with a formatted json. If not it is just trying to keep the conversation going to understand the user intent
         * but without answering with a formatted output. In this case the intent is None and the clarifying sentence is not used.
         */
        try{
            jsonData = new JSONObject(message.getContent());
        }catch (JSONException e){
            return new IntentResponse(IntentType.None,message.getContent());
        }

        IntentType intentType = IntentType.valueOf(jsonData.get("intent").toString());
        String clarifySentence = "";
        try {
            clarifySentence = jsonData.get("clarify_sentence").toString();
        } catch(Exception e){
             // this is the case where the intent has been identified and the clarifying sentence is not present in the json output
             }

        return new IntentResponse(intentType, clarifySentence != null ? clarifySentence.toString() : "");
    }

}


