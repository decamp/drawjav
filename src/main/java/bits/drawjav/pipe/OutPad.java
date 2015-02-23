package bits.drawjav.pipe;

import bits.util.ref.Refable;

/**
 * @author Philip DeCamp
 */
public interface OutPad extends Pad {

    /**
     * @return OKAY if ready to provide output, <br>
     *         FILL_FILTER if pad cannot produce more output until more data is provided to filter, <br>
     *         WAIT if pad cannot produce more output until later. Wait for a {@code RequestOutputEvent} on EventBus.<br>
     *         EXCEPTION if the pad cannot receive input due to an exception.
     *         CLOSED if pad cannot receive input because it is closed.
     */
    int status();

    /**
     * @param out Array to receive output.
     * @return OKAY if ready to provide output, <br>
     *         UNFINISHED if pad was unable to produce packet on this call. Caller may try again when ready. <br>
     *         FILL_FILTER if pad cannot produce more output until more data is provided to filter, <br>
     *         WAIT if pad cannot produce more output until later. An {@code OutPadReadyEvent} will be posted when ready.<br>
     *         EXCEPTION if the pad cannot receive input due to an exception.
     *         CLOSED if pad cannot receive input because it is closed.w
     */
    int poll( Refable[] out );

}
