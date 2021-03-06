package com.sky.easyIM.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;

import java.net.URI;

public class Client {

    private URI uri;

    private Bootstrap bootstrap;
    private EventLoopGroup group;

    private ChannelPromise channelPromise;

    private Channel channel;

    public Client(final URI uri) {
        this.uri = uri;
        this.init();
    }

    public void connect() {
        try {
            channel = bootstrap.connect(uri.getHost(), uri.getPort()).sync().channel();
            channelPromise.sync();
            System.out.println("connected success! and handshake complete!");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }



    private void init() {
        bootstrap = new Bootstrap();
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.option(ChannelOption.TCP_NODELAY, true);

        group = new NioEventLoopGroup();

        bootstrap.group(group).channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    protected void initChannel(SocketChannel channel) throws Exception {
                        ChannelPipeline pipeline = channel.pipeline();
                        pipeline.addLast(new HttpClientCodec());
                        pipeline.addLast(new HttpObjectAggregator(64*1024));
                        pipeline.addLast(new WebSocketHandler(getHandshaker(uri)));
                    }

                    private WebSocketClientHandshaker getHandshaker(final URI uri) {
                        return WebSocketClientHandshakerFactory.newHandshaker(uri,
                                WebSocketVersion.V13, null, false, null);
                    }
                });
    }

    private class WebSocketHandler extends SimpleChannelInboundHandler<Object> {

        private WebSocketClientHandshaker handshaker;

        public WebSocketHandler(final WebSocketClientHandshaker handshaker) {
            this.handshaker = handshaker;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            channelPromise = ctx.newPromise();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            this.handshaker.handshake(ctx.channel());
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object o) {
            System.out.println("receive data: " + o + " from address: " + ctx.channel().remoteAddress());
            if ( !handshaker.isHandshakeComplete() ) {
                try {
                    handshaker.finishHandshake(ctx.channel(), (FullHttpResponse) o);
                    channelPromise.setSuccess();
                    System.out.println("handshake success!");
                } catch (WebSocketHandshakeException e) {
                    e.printStackTrace();
                    channelPromise.setFailure(e);
                }
                return ;
            }
            if ( !(o instanceof TextWebSocketFrame) ) {
                System.out.println("received no text data: " + o);
                return ;
            }
            TextWebSocketFrame request = (TextWebSocketFrame) o;
            System.out.println("received text: " + request.text());

        }
    }

}