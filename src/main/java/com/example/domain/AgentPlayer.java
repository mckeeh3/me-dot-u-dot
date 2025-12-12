package com.example.domain;

public interface AgentPlayer {

  public enum Status {
    empty,
    in_progress,
    post_processing_review,
    post_processing_playbook_review,
    post_processing_system_prompt_review,
    completed
  }

  public record State(
      String sessionId,
      String gameId,
      DotGame.Player agent,
      Status status,
      int moveCount,
      int stepRetryCount,
      String postGameReview,
      String playbookReview,
      String systemPromptReview) {

    public static State empty() {
      return new State("", "", DotGame.Player.empty(), Status.empty, 0, 0, "", "", "");
    }

    public boolean isEmpty() {
      return sessionId.isEmpty();
    }

    public State withGameId(String newGameId) {
      return new State(sessionId, newGameId, agent, status, moveCount, stepRetryCount, postGameReview, playbookReview, systemPromptReview);
    }

    public State with(String newSessionId, String newGameId, DotGame.Player newAgent) {
      return new State(newSessionId, newGameId, newAgent, Status.in_progress, 0, 0, "", "", "");
    }

    public State withPostGameReview(String newPostGameReview) {
      return new State(sessionId, gameId, agent, Status.post_processing_review, moveCount, stepRetryCount, newPostGameReview, "", "");
    }

    public State withPlaybookReview(String newPlaybookReview) {
      return new State(sessionId, gameId, agent, Status.post_processing_playbook_review, moveCount, stepRetryCount, postGameReview, newPlaybookReview, "");
    }

    public State withSystemPromptReview(String newSystemPromptReview) {
      return new State(sessionId, gameId, agent, Status.post_processing_system_prompt_review, moveCount, stepRetryCount, postGameReview, playbookReview, newSystemPromptReview);
    }

    public State withMoveCount(int newMoveCount) {
      return new State(sessionId, gameId, agent, status, newMoveCount, stepRetryCount, postGameReview, playbookReview, systemPromptReview);
    }

    public State incrementStepRetryCount() {
      var newStepRetryCount = stepRetryCount + 1;
      return new State(sessionId, gameId, agent, status, moveCount, newStepRetryCount, postGameReview, playbookReview, systemPromptReview);
    }

    public State resetStepRetryCount() {
      return new State(sessionId, gameId, agent, status, moveCount, 0, postGameReview, playbookReview, systemPromptReview);
    }
  }

  public static String sessionId(String gameId, String agentId) {
    return gameId + "/" + agentId;
  }
}
