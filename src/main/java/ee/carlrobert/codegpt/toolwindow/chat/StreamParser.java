package ee.carlrobert.codegpt.toolwindow.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StreamParser {

  private static final String CODE_BLOCK_STARTING_REGEX = "```[a-zA-Z]*\n";
  private static final String TOOL_BLOCK_STARTING_REGEX = "\\{[^\"]*\"tool\":\s";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final StringBuilder messageBuilder = new StringBuilder();
  private boolean isProcessingCode;
  private boolean isProcessingTool;

  public List<StreamParseResponse> parse(String message) {
    message = message.replace("\r", "");
    messageBuilder.append(message);

    Pattern toolPattern = Pattern.compile(TOOL_BLOCK_STARTING_REGEX);
    Matcher toolMatcher = toolPattern.matcher(messageBuilder.toString());
    if (!isProcessingTool && !isProcessingCode && toolMatcher.find()) {
      isProcessingTool = true;
      var startingIndex = messageBuilder.indexOf(toolMatcher.group());
      var prevMessage = messageBuilder.substring(0, startingIndex);
      messageBuilder.delete(0, messageBuilder.indexOf(toolMatcher.group()));

      return List.of(
              new StreamParseResponse(StreamResponseType.TEXT, prevMessage),
              new StreamParseResponse(StreamResponseType.TOOL, messageBuilder.toString()));
    }

    if (isProcessingTool) {
      try {
        OBJECT_MAPPER.readTree(messageBuilder.toString());
        isProcessingTool = false;
        var toolRequest = messageBuilder.toString();
        clear();
        return List.of(new StreamParseResponse(StreamResponseType.TOOL, toolRequest));
      } catch (JsonProcessingException e) {
        // json response in progress
      }
    }

    Pattern codePattern = Pattern.compile(CODE_BLOCK_STARTING_REGEX);
    Matcher codeMatcher = codePattern.matcher(messageBuilder.toString());
    if (!isProcessingCode && !isProcessingTool && codeMatcher.find()) {
      isProcessingCode = true;

      var startingIndex = messageBuilder.indexOf(codeMatcher.group());
      var prevMessage = messageBuilder.substring(0, startingIndex);
      messageBuilder.delete(0, messageBuilder.indexOf(codeMatcher.group()));

      return List.of(
          new StreamParseResponse(StreamResponseType.TEXT, prevMessage),
          new StreamParseResponse(StreamResponseType.CODE, messageBuilder.toString()));
    }

    var codeEndingIndex = messageBuilder.indexOf("```\n", 1);
    if (isProcessingCode && codeEndingIndex > 0) {
      isProcessingCode = false;

      var codeResponse = messageBuilder.substring(0, codeEndingIndex + 3);
      messageBuilder.delete(0, codeEndingIndex + 3);

      return List.of(
          new StreamParseResponse(StreamResponseType.CODE, codeResponse),
          new StreamParseResponse(StreamResponseType.TEXT, messageBuilder.toString()));
    }

    var type = StreamResponseType.TEXT;
    if (isProcessingTool) {
      type = StreamResponseType.TOOL;
    } else if (isProcessingCode) {
      type = StreamResponseType.CODE;
    }

    return List.of(new StreamParseResponse(type, messageBuilder.toString()));
  }

  public void clear() {
    messageBuilder.setLength(0);
    isProcessingCode = false;
    isProcessingTool = false;
  }

  public record StreamParseResponse(StreamResponseType type, String response) {
  }
}
