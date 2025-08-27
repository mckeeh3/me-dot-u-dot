package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueType;

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

    config.getObject("akka.javasdk.agent").entrySet()
        .forEach(entry -> {
          var value = entry.getValue();

          if (value.valueType() == ConfigValueType.OBJECT) {
            log.info("================================================================================");
            log.info("Reference configuration: {} ({})", entry.getKey(), value.valueType().name());
            log.info("--------------------------------------------------------------------------------");

            var modelConfig = config.getConfig("akka.javasdk.agent." + entry.getKey());
            modelConfig.entrySet()
                .forEach(field -> {
                  if ("api-key".equals(field.getKey())) {
                    log.info("Field: {}: {}", field.getKey(), "********");
                  } else {
                    log.info("Field: {}: {}", field.getKey(), field.getValue().unwrapped().toString());
                  }
                });
          } else {
            var configValueType = value.valueType().name();
            log.info("================================================================================");
            log.info("Reference configuration: {} = {} ({})", entry.getKey(), value.unwrapped().toString(), configValueType);
            log.info("--------------------------------------------------------------------------------");
          }
        });

    config.root().entrySet().stream().filter(entry -> entry.getKey().startsWith("ai-agent-model-"))
        .forEach(entry -> {
          log.info("================================================================================");
          log.info("Dot Game Agent model configuration: {}", entry.getKey());
          log.info("--------------------------------------------------------------------------------");

          var modelConfigName = entry.getKey().substring("ai.agent.model.".length());
          log.info("Model config name: {}", modelConfigName);

          var modelConfig = config.getConfig(entry.getKey());
          modelConfig.entrySet().forEach(modelEntry -> {
            if ("api-key".equals(modelEntry.getKey())) {
              log.info("Model entry: {}: {}", modelEntry.getKey(), "********");
            } else {
              log.info("Model entry: {}: {}", modelEntry.getKey(), modelEntry.getValue().unwrapped().toString());
            }
          });
        });
  }
}
