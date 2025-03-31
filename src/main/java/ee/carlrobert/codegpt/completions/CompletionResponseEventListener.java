package ee.carlrobert.codegpt.completions;

import ee.carlrobert.codegpt.conversations.Conversation;
import ee.carlrobert.codegpt.conversations.message.Message;
import ee.carlrobert.codegpt.events.CodeGPTEvent;
import ee.carlrobert.llm.client.openai.completion.ErrorDetails;

public interface CompletionResponseEventListener {

  void handleMessage(String message);

  void handleError(ErrorDetails error, Throwable ex);

  void handleTokensExceeded(Conversation conversation, Message message);

  void handleCompleted(String fullMessage, ChatCompletionParameters callParameters);

  void handleCodeGPTEvent(CodeGPTEvent event);

  void handleRequestOpen();
}
