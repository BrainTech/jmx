/**
 * 
 */
package multiplexer.jmx;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;

import com.google.common.collect.LinkedListMultimap;

/**
 * @author Kasia Findeisen
 * @author Piotr Findeisen
 */
public class ConnectionsMap {

	/**
	 * A multimap of {@link Channel}s grouped by connected peers' types.
	 */
	private LinkedListMultimap<Integer, Channel> channelsByType = LinkedListMultimap
		.create();

	/**
	 * Provides access to all open channels while cleaning up.
	 */
	private ChannelGroup allChannels = new DefaultChannelGroup();

	/**
	 * A map of {@link Channel}s by peer Id. Id is associated with the peer's
	 * most recent connection.
	 */
	private Map<Long, Channel> channelsByPeerId = new HashMap<Long, Channel>();

	/**
	 * Helper {@link Map}, reverse to {@code channelsByType}, which is a
	 * {@link LinkedListMultimap} and therefore has no reverse access.
	 */
	private Map<Channel, Integer> peerTypesByChannel = new HashMap<Channel, Integer>();

	/**
	 * Adds a new channel to {@code allChannels} which is a {@link ChannelGroup}
	 * . Any closed channel will be removed automatically.
	 * 
	 * @param channel
	 *            a new connection
	 */
	public void addNew(Channel channel) {
		allChannels.add(channel);
	}

	/**
	 * Adds a new {@link Channel}, together with the connected peer's Id (
	 * {@code peerId}) and the connected peer's type ({@code peerType}) to
	 * global maps allowing indexing by peer types and Id. If a connection with
	 * the peer (a peer having the same Id as {@code peerId}) is already
	 * established, it is overwritten in the maps. The {@link Channel} of the
	 * previous connection is returned so that it can be closed by the callee.
	 * 
	 * @param channel
	 *            a new connection
	 * @param peerId
	 *            Id of the connected peer
	 * @param peerType
	 *            type of the connected peer
	 * @return a channel of a previous connection to the peer or null if the
	 *         peer wasn't connected
	 */
	public synchronized Channel add(Channel channel, long peerId, int peerType) {
		/* channelsByType.remove(UNGROUPPED_CHANNELS, channel); */
		Channel oldChannel = channelsByPeerId.put(peerId, channel);
		if (oldChannel != null) {
			channelsByType.remove(peerTypesByChannel.get(oldChannel),
				oldChannel);
			peerTypesByChannel.remove(oldChannel);
		}
		peerTypesByChannel.put(channel, peerType);
		channelsByType.put(peerType, channel);
		return oldChannel;
	}

	/**
	 * Returns a {@link Channel} associated with some peer of the given type (
	 * {@code peerType}). Chooses the channel on a basis of round-robin
	 * algorithm. TODO rzuca wyjątek, gdy nie ma peerów tego typu
	 * 
	 * @param peerType
	 *            requested type of the peer
	 * @return
	 */
	public synchronized Channel getAny(int peerType) {
		List<Channel> list = channelsByType.get(peerType);
		Channel anyChannel = list.remove(0);
		list.add(anyChannel);
		return anyChannel;
	}

	/**
	 * Returns an {@Link Iterator} of all {@link Channel}s associated
	 * with the given peer type ({@code peerType}). You should manually
	 * synchronize on this {@link ConnectionsMap} when calling this method and
	 * iterating over the returned value.
	 * 
	 * @param peerType
	 *            requested type of the peer
	 * @return
	 */
	public Iterator<Channel> getAll(int peerType) {
		List<Channel> list = channelsByType.get(peerType);
		return list.iterator();
	}

	/**
	 * Get all {@link Channel}s that have been added with {@link #addNew} and
	 * has not yet been closed. You should not modify the returned set.
	 * 
	 * @return all channels
	 */
	public ChannelGroup getAllChannels() {
		return allChannels;
	}
}
