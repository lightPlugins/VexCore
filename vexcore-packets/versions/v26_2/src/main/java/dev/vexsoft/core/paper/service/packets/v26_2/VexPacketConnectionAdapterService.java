package dev.vexsoft.core.paper.service.packets.v26_2;

import io.netty.channel.ChannelPromise;
import java.util.UUID;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.service.packets.PacketConnectionAdapterService;
import dev.vexsoft.core.paper.packets.internal.PacketDuplexHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import java.util.Objects;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

@Dependencies
public final class VexPacketConnectionAdapterService implements PacketConnectionAdapterService {

  private static final String PACKET_HANDLER = "packet_handler";
  private static final String HANDLER_NAME = "vexcore_packets";

  public VexPacketConnectionAdapterService(final VexServiceRegistry services) {
  }

  @Override
  public void inject(final Player player, final PacketDuplexHandler handler) {
    Channel channel = channel(player);
    channel.eventLoop().execute(() -> addHandler(
        channel.pipeline(),
        player.getUniqueId(),
        handler
    ));
  }

  @Override
  public void uninject(final Player player) {
    Channel channel = channel(player);
    channel.eventLoop().execute(() -> {
      ChannelPipeline pipeline = channel.pipeline();
      if (pipeline.get(HANDLER_NAME) != null) {
        pipeline.remove(HANDLER_NAME);
      }
    });
  }

  private static void addHandler(
      final ChannelPipeline pipeline,
      final UUID viewerId,
      final PacketDuplexHandler handler
  ) {
    if (pipeline.get(HANDLER_NAME) != null) {
      return;
    }
    ChannelDuplexHandler channelHandler = new ChannelDuplexHandler() {
      @Override
      public void write(
          final ChannelHandlerContext context,
          final Object message,
          final ChannelPromise promise
      ) throws Exception {
        Object result = handler.write(viewerId, message);
        if (result != null) {
          super.write(context, result, promise);
        } else {
          promise.setSuccess();
        }
      }

      @Override
      public void channelRead(final ChannelHandlerContext context, final Object message)
          throws Exception {
        Object result = handler.read(viewerId, message);
        if (result != null) {
          super.channelRead(context, result);
        }
      }
    };
    if (pipeline.get(PACKET_HANDLER) != null) {
      pipeline.addBefore(PACKET_HANDLER, HANDLER_NAME, channelHandler);
    } else {
      pipeline.addLast(HANDLER_NAME, channelHandler);
    }
  }

  private static Channel channel(final Player player) {
    CraftPlayer craftPlayer = (CraftPlayer) Objects.requireNonNull(player, "player");
    return craftPlayer.getHandle().connection.connection.channel;
  }
}
