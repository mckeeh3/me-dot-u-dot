package com.example.application;

import java.util.List;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.domain.Player;

@ComponentId("player-view")
public class PlayerView extends View {

  @Query("""
      SELECT * AS players
        FROM player_view
       ORDER BY name ASC
      """)
  public QueryEffect<Players> getAllNames() {
    return queryResult();
  }

  @Consume.FromKeyValueEntity(PlayerEntity.class)
  public static class ById extends TableUpdater<PlayerRow> {
    public Effect<PlayerRow> onChange(Player.State state) {

      return effects().updateRow(new PlayerRow(
          state.id(),
          state.name(),
          state.type().name(),
          state.model()));
    }
  }

  public record PlayerRow(String id, String name, String type, String model) {}

  public record Players(List<PlayerRow> players) {}
}
