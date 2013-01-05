package network;

import java.net.InetAddress;

/**
 * // TODO: Doc
 *
 * @author Erik
 * @date 04-01-13, 23:41
 */
public interface IPChangedObserver {
    public void ipChanged(InetAddress address);
}
