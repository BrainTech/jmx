// Copyright 2009 Warsaw University, Faculty of Physics
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

package multiplexer.jmx.internal;

import multiplexer.protocol.Constants.MessageTypes;
import multiplexer.protocol.Protocol.MultiplexerMessage;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.timeout.IdleState;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Piotr Findeisen
 */
@ChannelPipelineCoverage("all")
public class HeartbitHandler implements ChannelUpstreamHandler {

	private static final Logger logger = LoggerFactory
		.getLogger(HeartbitHandler.class);

	public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
		throws Exception {

		if (e instanceof IdleStateEvent) {
			// The channel was idle for too long.
			IdleStateEvent evt = (IdleStateEvent) e;
			if (evt.getState() == IdleState.READER_IDLE) {
				// No incoming HEARTBITs nor any other messages.
				double idleTimeSecs = (System.currentTimeMillis() - evt
					.getLastActivityTimeMillis()) / 1000.0;
				logger.warn("Peer idle for {}s, closing connection.",
					idleTimeSecs);
				Channels.close(e.getChannel());

			} else if (evt.getState() == IdleState.WRITER_IDLE) {
				// No messages sent out, let's send a HEARTBIT.
				Channels.write(ctx.getChannel(), MultiplexerMessage
					.newBuilder().setType(MessageTypes.HEARTBIT).build());

			} else {
				throw new AssertionError("We do not set " + IdleState.ALL_IDLE
					+ " idle timeouts.");
			}
			return;
		}

		if (e instanceof MessageEvent) {
			Object message = ((MessageEvent) e).getMessage();
			if (message instanceof MultiplexerMessage
				&& ((MultiplexerMessage) message).getType() == MessageTypes.HEARTBIT) {
				// Don't pass on incoming HEARTBITs, they already served their
				// purpose.
				return;
			}
		}

		// By default ...
		ctx.sendUpstream(e);
	}
}
