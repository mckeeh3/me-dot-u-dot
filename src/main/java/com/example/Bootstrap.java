package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;

@Setup
public class Bootstrap implements ServiceSetup {
  static Logger log = LoggerFactory.getLogger(Bootstrap.class);

  public Bootstrap(Config config) {
    if (config.getString("akka.javasdk.agent.model-provider").equals("openai") &&
        config.getString("akka.javasdk.agent.openai.api-key").isBlank()) {
      throw new IllegalStateException(
          "No API keys found. Make sure you have OPENAI_API_KEY defined as environment variable, or change the model provider configuration in application.conf to use a different LLM.");
    }

    log.info("akka.javasdk.agent");
    config.getObject("akka.javasdk.agent").unwrapped().forEach((key, value) -> {
      log.info("Model provider: {}: {}", key, value);
    });

    log.info("agent-1");
    config.getObject("agent-1").unwrapped().forEach((key, value) -> {
      log.info("Model provider: {}: {}", key, value);
    });
  }
}
