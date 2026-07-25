package com.rudder.server.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
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
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
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
import java.util.concurrent.TimeUnit;

/**
 * Netty 4.x WebSocket 推送：ws://host:8081/ws?workspaceId=...
 * 心跳使用协议层 Ping/Pong（IdleStateHandler 写空闲主动 Ping，读空闲断连），无自定义应用层协议。
 */
@Slf4j
@Component
public class NettyWsHub {

    private static final AttributeKey<WebSocketServerHandshaker> HANDSHAKER = AttributeKey.valueOf("handshaker");
    private static final AttributeKey<Long> WS_ID = AttributeKey.valueOf("workspaceId");
    /** 是否已完成 WS 握手；握手前不发 Ping，避免干扰 HTTP Upgrade。 */
    private static final AttributeKey<Boolean> WS_READY = AttributeKey.valueOf("wsReady");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, ChannelGroup> rooms = new ConcurrentHashMap<>();

    @Value("${rudder.ws.port:8081}")
    private int port;

    /** 写空闲秒数：到期主动发 Ping（0 关闭）。 */
    @Value("${rudder.ws.writer-idle-seconds:30}")
    private int writerIdleSeconds;

    /** 读空闲秒数：到期认为对端失联并关闭（0 关闭）。应大于 writer-idle。 */
    @Value("${rudder.ws.reader-idle-seconds:60}")
    private int readerIdleSeconds;

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
                        // reader / writer / all；all=0 表示不启用
                        p.addLast(new IdleStateHandler(readerIdleSeconds, writerIdleSeconds, 0, TimeUnit.SECONDS));
                        p.addLast(new HubHandler());
                    }
                });
        serverChannel = b.bind(port).sync().channel();
        log.info("Netty WebSocket hub on port {} (ping writerIdle={}s readerIdle={}s)",
                port, writerIdleSeconds, readerIdleSeconds);
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

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent idle) {
                Boolean ready = ctx.channel().attr(WS_READY).get();
                if (!Boolean.TRUE.equals(ready)) {
                    // 握手未完成：读空闲直接关；写空闲忽略
                    if (idle.state() == IdleState.READER_IDLE) {
                        ctx.close();
                    }
                    return;
                }
                if (idle.state() == IdleState.WRITER_IDLE) {
                    // 主动 Ping；浏览器等客户端会在协议层自动回 Pong
                    ctx.writeAndFlush(new PingWebSocketFrame());
                    log.trace("ws ping sent channel={}", ctx.channel().id());
                } else if (idle.state() == IdleState.READER_IDLE) {
                    log.debug("ws reader idle, closing channel={}", ctx.channel().id());
                    ctx.writeAndFlush(new CloseWebSocketFrame(1001, "idle timeout"))
                            .addListener(ChannelFutureListener.CLOSE);
                }
                return;
            }
            super.userEventTriggered(ctx, evt);
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
            ctx.channel().attr(WS_READY).set(true);
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
                // 客户端主动 Ping → 回 Pong
                ctx.writeAndFlush(new PongWebSocketFrame(ping.content().retain()));
                return;
            }
            if (frame instanceof PongWebSocketFrame) {
                // 服务端 Ping 的应答；读事件已重置 IdleStateHandler，由 SimpleChannelInboundHandler 释放帧
                return;
            }
            // Text frames：业务仍以服务端推送为主；客户端文本暂忽略
        }

        private void send(ChannelHandlerContext ctx, HttpResponseStatus status) {
            DefaultFullHttpResponse res = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer(status.toString(), CharsetUtil.UTF_8));
            ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
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
