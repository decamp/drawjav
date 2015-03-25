package bits.drawjav.pipe;

import bits.util.ref.Refable;

/**
 * @author Philip DeCamp
 */
public interface InPad<T extends Refable> extends Pad {

    /**
     * @return OKAY if ready to receive input, <br>
     *         DRAIN_UNIT if pad cannot receive more input until output is drained. <br>
     *         WAIT if pad cannot receive input until later. Wait for a {@code InPadReadyEvent} on EventBus<br>
     *         CLOSED if pad cannot receive input because it is closed.
     *         EXCEPTION if the pad cannot receive input due to an exception.
     */
    int status();

    /**
     * @param packet Packet to receive, or {@code null} to drain.
     *
     * @return OKAY if packet was received, <br>
     *         UNFINISHED if pad was unable to consume packet on this call. Caller may try again when ready. <br>
     *         DRAIN_UNIT if pad cannot receive more input until output is drained. <br>
     *         WAIT if pad cannot receive input until later. Wait for a {@code InPadReadyEvent} on EventBus<br>
     *         EXCEPTION if the pad cannot receive input due to an exception.
     */
    int offer( T packet );

}
