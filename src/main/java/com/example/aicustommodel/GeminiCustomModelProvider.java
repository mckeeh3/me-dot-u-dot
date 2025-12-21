package com.example.aicustommodel;

import akka.javasdk.agent.ModelProvider;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;

public class GeminiCustomModelProvider implements ModelProvider.Custom {

  @Override
  public Object createChatModel() {
    return GoogleAiGeminiChatModel.builder()
        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
        .modelName("gemini-2.5-flash")
        .build();
  }

  @Override
  public Object createStreamingChatModel() {
    return GoogleAiGeminiStreamingChatModel.builder()
        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
        .modelName("gemini-2.5-flash")
        .build();
  }
}
