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
      SELECT name AS names
        FROM player_view
       ORDER BY name ASC
      """)
  public QueryEffect<Names> getAllNames() {
    return queryResult();
  }

  @Consume.FromKeyValueEntity(PlayerEntity.class)
  public static class ById extends TableUpdater<PlayerRow> {
    public Effect<PlayerRow> onChange(Player.State state) {
      if (state.isEmpty())
        return effects().deleteRow(entityId());
      return effects().updateRow(new PlayerRow(state.id(), state.name()));
    }
  }

  public record PlayerRow(String id, String name) {}

  public record Names(List<String> names) {}
}
