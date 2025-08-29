package com.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;
import com.example.domain.PlayerGames;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;

@ComponentId("dot-game-to-player-games-consumer")
@Consume.FromEventSourcedEntity(DotGameEntity.class)
public class DotGameToPlayerGamesConsumer extends Consumer {
  final Logger log = LoggerFactory.getLogger(getClass());
  final ComponentClient componentClient;

  public DotGameToPlayerGamesConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(DotGame.Event event) {
    return switch (event) {
      case DotGame.Event.GameResults e -> onEvent(e);
      default -> effects().done();
    };
  }

  Effect onEvent(DotGame.Event.GameResults event) {
    log.debug("Event: {}", event);

    var player1 = event.player1Status();
    var player2 = event.player2Status();

    var gameId = event.gameId();

    var player1Win = player1.isWinner();
    var player1Lose = !player1Win;
    var player1Draw = !player1Win && !player1Lose;

    var player2Win = player2.isWinner();
    var player2Lose = !player2Win;
    var player2Draw = !player2Win && !player2Lose;

    var gameStatsPlayer1 = new PlayerGames.GameStats(1, player1Win ? 1 : 0, player1Lose ? 1 : 0, player1Draw ? 1 : 0);
    var gameStatsPlayer2 = new PlayerGames.GameStats(1, player2Win ? 1 : 0, player2Lose ? 1 : 0, player2Draw ? 1 : 0);

    var addGamePlayer1 = new PlayerGames.Command.AddGame(player1.player().id(), gameId, gameStatsPlayer1);
    var addGamePlayer2 = new PlayerGames.Command.AddGame(player2.player().id(), gameId, gameStatsPlayer2);

    componentClient.forEventSourcedEntity(player1.player().id())
        .method(PlayerGamesEntity::addGame)
        .invoke(addGamePlayer1);

    componentClient.forEventSourcedEntity(player2.player().id())
        .method(PlayerGamesEntity::addGame)
        .invoke(addGamePlayer2);

    return effects().done();
  }
}
