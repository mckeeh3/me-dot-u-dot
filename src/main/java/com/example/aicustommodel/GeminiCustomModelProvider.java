package com.example.aicustommodel;

import akka.javasdk.agent.ModelProvider;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeminiCustomModelProvider implements ModelProvider.Custom {
  static final Logger log = LoggerFactory.getLogger(GeminiCustomModelProvider.class);
  static final String modelName = "gemini-3-pro-preview"; // gemini-2.5-flash"; // gemini-3-pro-preview

  @Override
  public Object createChatModel() {
    log.info("Creating Gemini chat model: {}", modelName);
    return GoogleAiGeminiChatModel.builder()
        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
        .modelName(modelName) // gemini-2.5-flash")
        .build();
    // throw new UnsupportedOperationException("Unimplemented method 'createChatModel'");
  }

  @Override
  public Object createStreamingChatModel() {
    log.info("Creating Gemini streaming chat model: {}", modelName);
    return GoogleAiGeminiStreamingChatModel.builder()
        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
        .modelName(modelName) // gemini-2.5-flash")
        .build();
    // throw new UnsupportedOperationException("Unimplemented method 'createStreamingChatModel'");
  }
}
