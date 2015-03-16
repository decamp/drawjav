package bits.drawjav.pipe;

import bits.drawjav.StreamFormat;
import java.io.IOException;

/**
 * A Pad is like a slot or a port on a filter that either receives input packets or delivers output packets.
 * There are two main types: InPads and OutPads, which represent filter inputs and filter outputs respectively.
 * <p>
 * Additionally, pads come in threaded or unthreaded varieties.
 * <p>
 * Unhreaded pads:
 * <ul>
 * <li>Do not generate RequestInputEvents.</li>
 * <li>Do not have locks</li>
 * <li>Only report overflow when the OutPads on the owning filter must be drained.
 * </ul>
 *
 * Threaded pads:
 * <ul>
 * <li>Generate RequestInputEvents when they become able to accept input.
 * <li>May have locks, which may be used by other threads to wait for state changes.</li>
 * <li>May have multiple reasons for overflowing:  1) the OutPads of owning filter must be drained, 2) the
 * caller must wait until more input is requested.
 * </ul>
 *
 * @author Philip DeCamp
 */
public interface Pad {

    public static final int CLOSED       = -2;
    public static final int EXCEPTION    = -1;
    public static final int OKAY         =  0;
    public static final int UNFINISHED   =  1;
    public static final int FILL_FILTER  =  2;
    public static final int DRAIN_FILTER =  3;
    public static final int WAIT         =  4;

    /**
     * Must be called before opening Filter.
     * @param stream The stream handle for the incoming data,
     *               or {@code null} if the pad will not be opened.
     */
    void config( StreamFormat stream ) throws IOException;

    /**
     * @return true iff this is a threaded pad.
     */
    boolean isThreaded();

    /**
     * @return Synchronization object for this pad. May be {@code null}.
     */
    Object lock();

    /**
     * If there was an error on the pad, calling {@code exception()}
     * will retrieve that error.
     *
     * @return previous exception
     */
    Exception exception();

}
