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
      String gameReview,
      String playbookReview,
      String systemPromptReview) {

    public static State empty() {
      return new State("", "", DotGame.Player.empty(), Status.empty, "", "", "");
    }

    public boolean isEmpty() {
      return sessionId.isEmpty();
    }

    public State with(String newSessionId, String newGameId, DotGame.Player newAgent) {
      return new State(newSessionId, newGameId, newAgent, Status.in_progress, "", "", "");
    }

    public State withGameReview(String newGameReview) {
      return new State(sessionId, gameId, agent, Status.post_processing_review, newGameReview, "", "");
    }

    public State withPlaybookReview(String newPlaybookReview) {
      return new State(sessionId, gameId, agent, Status.post_processing_playbook_review, gameReview, newPlaybookReview, "");
    }

    public State withSystemPromptReview(String newSystemPromptReview) {
      return new State(sessionId, gameId, agent, Status.post_processing_system_prompt_review, gameReview, playbookReview, newSystemPromptReview);
    }
  }

  public static String sessionId(String gameId, String agentId) {
    return gameId + "/" + agentId;
  }
}
