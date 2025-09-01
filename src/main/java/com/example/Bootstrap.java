package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValueType;

import akka.javasdk.JsonSupport;
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

    config.root().entrySet().stream()
        .filter(entry -> entry.getKey().startsWith("ai-agent-model-"))
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

    // Search for a specific value in the config
    // var searchKey = "persistence";
    // log.info("================================================================================");
    // log.info("Search config for: {}", searchKey);
    // searchConfig(searchKey, "", config.root());
    // log.info("--------------------------------------------------------------------------------");

    // log.info("================================================================================");
    // log.info("Full config listing - start");
    // log.info("--------------------------------------------------------------------------------");
    // logConfig("", config.root());
    // log.info("--------------------------------------------------------------------------------");
    // log.info("Full config listing - end");
    // log.info("--------------------------------------------------------------------------------");
  }

  static void searchConfig(String searchValue, String prefix, ConfigObject configObject) {
    configObject.entrySet().forEach(entry -> {
      var keyValue = "";
      if (entry.getValue().valueType() == ConfigValueType.OBJECT) {
        searchConfig(searchValue, prefix + entry.getKey() + ".", (ConfigObject) entry.getValue());
      } else if (entry.getValue() == null || entry.getValue().unwrapped() == null) {
        keyValue = "%s = null".formatted(prefix + entry.getKey());
      } else {
        keyValue = "%s = %s".formatted(prefix + entry.getKey(), entry.getValue().unwrapped().toString());
      }

      if (keyValue.contains(searchValue)) {
        log.info("Match found: {}", keyValue);
      }
    });
  }

  static void logConfig(String prefix, ConfigObject configObject) {
    configObject.entrySet().forEach(entry -> {
      if (entry.getValue().valueType() == ConfigValueType.OBJECT) {
        var om = JsonSupport.getObjectMapper();
        try {
          // var json = om.writeValueAsString(entry.getValue().unwrapped());
          var json = om.writerWithDefaultPrettyPrinter().writeValueAsString(entry.getValue().unwrapped());
          var maskedJson = maskSensitiveFields(json);
          log.info("{} = (JSON)\n{}", prefix + entry.getKey(), maskedJson);
        } catch (JsonProcessingException e) {
          e.printStackTrace();
        }
        logConfig(prefix + entry.getKey() + ".", (ConfigObject) entry.getValue());
      } else if (entry.getValue() == null || entry.getValue().unwrapped() == null) {
        log.info("{} = {} ({})", prefix + entry.getKey(), "null", "null");
      } else if (isSensitiveField(entry.getKey())) {
        log.info("{} = {} ({})", prefix + entry.getKey(), "********", entry.getValue().valueType().toString());
      } else {
        log.info("{} = {} ({})", prefix + entry.getKey(), entry.getValue().unwrapped().toString(), entry.getValue().valueType().toString());
      }
    });
  }

  /**
   * Masks sensitive fields in JSON strings by replacing their values with "********"
   *
   * @param jsonString The JSON string to mask
   * @return The masked JSON string
   */
  static String maskSensitiveFields(String jsonString) {
    try {
      var om = new ObjectMapper();
      var rootNode = om.readTree(jsonString);

      // Recursively mask sensitive fields
      var maskedNode = maskSensitiveFieldsRecursive(rootNode);

      return om.writerWithDefaultPrettyPrinter().writeValueAsString(maskedNode);
    } catch (Exception e) {
      // If masking fails, return original string to avoid breaking the application
      log.warn("Failed to mask sensitive fields in JSON: {}", e.getMessage());
      return jsonString;
    }
  }

  /**
   * Recursively traverses JSON nodes to mask sensitive field values
   *
   * @param node The JSON node to process
   * @return The masked JSON node
   */
  static JsonNode maskSensitiveFieldsRecursive(JsonNode node) {
    if (node.isObject()) {
      var objectNode = (ObjectNode) node;
      var maskedNode = objectNode.deepCopy();

      // Check each field in the object
      var fieldNames = objectNode.fieldNames();
      while (fieldNames.hasNext()) {
        var fieldName = fieldNames.next();
        if (isSensitiveField(fieldName)) {
          maskedNode.put(fieldName, "********");
        } else {
          var fieldValue = objectNode.get(fieldName);
          if (fieldValue.isObject() || fieldValue.isArray()) {
            maskedNode.set(fieldName, maskSensitiveFieldsRecursive(fieldValue));
          }
        }
      }
      return maskedNode;
    } else if (node.isArray()) {
      // Handle arrays by masking each element
      for (int i = 0; i < node.size(); i++) {
        var element = node.get(i);
        if (element.isObject() || element.isArray()) {
          maskSensitiveFieldsRecursive(element);
        }
      }
    }

    return node;
  }

  /**
   * Determines if a field name contains sensitive information
   *
   * @param fieldName The field name to check
   * @return true if the field is sensitive, false otherwise
   */
  static boolean isSensitiveField(String fieldName) {
    var lowerFieldName = fieldName.toLowerCase();
    return lowerFieldName.contains("api-key") ||
        lowerFieldName.contains("apikey") ||
        lowerFieldName.contains("secret") ||
        lowerFieldName.contains("password") ||
        // lowerFieldName.contains("token") ||
        lowerFieldName.contains("credential") ||
        lowerFieldName.contains("key") ||
        lowerFieldName.contains("auth");
  }
}
