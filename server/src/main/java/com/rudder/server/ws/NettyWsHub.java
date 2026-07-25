package com.rudder.server.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.buffer.Unpooled;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Netty 4.x WebSocket 推送：ws://host:8081/ws?workspaceId=...
 */
@Slf4j
@Component
public class NettyWsHub {

    private static final AttributeKey<WebSocketServerHandshaker> HANDSHAKER = AttributeKey.valueOf("handshaker");
    private static final AttributeKey<Long> WS_ID = AttributeKey.valueOf("workspaceId");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, ChannelGroup> rooms = new ConcurrentHashMap<>();

    @Value("${rudder.ws.port:8081}")
    private int port;

    private EventLoopGroup boss;
    private EventLoopGroup worker;
    private Channel serverChannel;

    @PostConstruct
    public void start() throws InterruptedException {
        boss = new NioEventLoopGroup(1);
        worker = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new HttpServerCodec());
                        p.addLast(new HttpObjectAggregator(65536));
                        p.addLast(new HubHandler());
                    }
                });
        serverChannel = b.bind(port).sync().channel();
        log.info("Netty WebSocket hub on port {}", port);
    }

    @PreDestroy
    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (boss != null) {
            boss.shutdownGracefully();
        }
        if (worker != null) {
            worker.shutdownGracefully();
        }
    }

    public void publish(Long workspaceId, Map<String, Object> event) {
        ChannelGroup group = rooms.get(workspaceId);
        if (group == null || group.isEmpty()) {
            return;
        }
        try {
            group.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(event)));
        } catch (Exception e) {
            log.warn("ws publish failed: {}", e.getMessage());
        }
    }

    private class HubHandler extends SimpleChannelInboundHandler<Object> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof FullHttpRequest req) {
                handleHttp(ctx, req);
            } else if (msg instanceof WebSocketFrame frame) {
                handleWs(ctx, frame);
            }
        }

        private void handleHttp(ChannelHandlerContext ctx, FullHttpRequest req) {
            if (!HttpMethod.GET.equals(req.method())) {
                send(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
                return;
            }
            QueryStringDecoder q = new QueryStringDecoder(req.uri());
            if (!q.path().startsWith("/ws")) {
                send(ctx, HttpResponseStatus.NOT_FOUND);
                return;
            }
            List<String> ids = q.parameters().get("workspaceId");
            if (ids == null || ids.isEmpty()) {
                send(ctx, HttpResponseStatus.BAD_REQUEST);
                return;
            }
            Long workspaceId = Long.parseLong(ids.get(0));
            String location = "ws://" + req.headers().get(HttpHeaderNames.HOST) + "/ws";
            WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(location, null, true);
            WebSocketServerHandshaker handshaker = factory.newHandshaker(req);
            if (handshaker == null) {
                WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
                return;
            }
            handshaker.handshake(ctx.channel(), req);
            ctx.channel().attr(HANDSHAKER).set(handshaker);
            ctx.channel().attr(WS_ID).set(workspaceId);
            rooms.computeIfAbsent(workspaceId, id -> new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)).add(ctx.channel());
        }

        private void handleWs(ChannelHandlerContext ctx, WebSocketFrame frame) {
            if (frame instanceof CloseWebSocketFrame close) {
                WebSocketServerHandshaker hs = ctx.channel().attr(HANDSHAKER).get();
                if (hs != null) {
                    hs.close(ctx.channel(), close.retain());
                }
                return;
            }
            if (frame instanceof PingWebSocketFrame ping) {
                ctx.writeAndFlush(new PongWebSocketFrame(ping.content().retain()));
            }
            // Text frames ignored (server push only)
        }

        private void send(ChannelHandlerContext ctx, HttpResponseStatus status) {
            DefaultFullHttpResponse res = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer(status.toString(), CharsetUtil.UTF_8));
            ctx.writeAndFlush(res).addListener(io.netty.channel.ChannelFutureListener.CLOSE);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            Long wsId = ctx.channel().attr(WS_ID).get();
            if (wsId != null) {
                ChannelGroup g = rooms.get(wsId);
                if (g != null) {
                    g.remove(ctx.channel());
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.debug("ws channel error: {}", cause.getMessage());
            ctx.close();
        }
    }
}
