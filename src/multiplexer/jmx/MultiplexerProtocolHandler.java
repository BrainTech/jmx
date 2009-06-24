package multiplexer.jmx;


import multiplexer.Multiplexer.MultiplexerMessage;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.timeout.IdleState;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelPipelineCoverage("all")
class MultiplexerProtocolHandler extends SimpleChannelHandler {

	private static final Logger logger = LoggerFactory.getLogger(SimpleChannelHandler.class);

	private ConnectionsManager connectionsManager;

	public MultiplexerProtocolHandler(ConnectionsManager connectionsManager) {
		this.connectionsManager = connectionsManager;
	}

	@Override
	public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
			throws Exception {
		if (e instanceof IdleStateEvent) {
			IdleStateEvent evt = (IdleStateEvent) e;
			if (evt.getState() == IdleState.READER_IDLE) {
				double idleTimeSecs = (System.currentTimeMillis() - evt.getLastActivityTimeMillis()) / 1000.0;
				logger.warn("Peer idle for {}s, closing connection.", idleTimeSecs);
				connectionsManager.close(evt.getChannel());
			} else if (evt.getState() == IdleState.WRITER_IDLE) {
				connectionsManager.sendHeartbit(ctx.getChannel());
			} else {
				assert false : "We do not set " + IdleState.ALL_IDLE + " idle timeouts.";
			}
		} else {
			super.handleUpstream(ctx, e);
		}
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
		System.err.println("messageReceived");
		assert e.getMessage() instanceof MultiplexerMessage : e.getMessage()
				+ " is not a MultiplexerMessage";
		System.err.println(e.getMessage()); // TODO debug
		connectionsManager.messageReceived((MultiplexerMessage) e.getMessage(),
				ctx.getChannel());
	}

	@Override
	public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
			throws Exception {

		assert e.getMessage() instanceof MultiplexerMessage : "You should feed the channel with MultiplexerMessages, not "
				+ e.getMessage();
		super.writeRequested(ctx, e);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
		logger.warn("Unhandled exception", e.getCause());
		e.getChannel().close();
	}
}
