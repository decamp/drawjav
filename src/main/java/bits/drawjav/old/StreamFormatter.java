/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.old;

import bits.drawjav.*;

import java.io.*;
import java.nio.channels.ClosedChannelException;


/**
 * @author decamp
 */
@Deprecated
public interface StreamFormatter {

    /**
     * Opens a video stream, formats all packets on that stream to
     * specified format, and passes them on to {@code input}.
     *
     * @param source        Specifies source of the stream.
     *                      Some StreamFormatters may only work with a pre-defined source or set of sources.
     *                      In this case, {@code source} must be one of these sources or {@code null}.
     * @param sourceStream  Stream to open. MUST belong to {@code source}.
     * @param destFormat    Desired destination format, or {@code null} if no formatting is requested.
     * @param sink          Sink to receive processed packets.
     * @return StreamHandle for newly created, formatted stream.
     * @throws ClosedChannelException if StreamFormatter is closed.
     * @throws IllegalArgumentException if source/sourceStream are invalid.
     * @throws IOException for most other failures.
     */
    public Stream openVideoStream( PacketReader source,
                                   Stream sourceStream,
                                   StreamFormat destFormat,
                                   Sink<? super DrawPacket> sink )
                                   throws IOException;

    /**
     * Opens an audio stream, formats all packets on that stream to
     * specified format, and passes them on to {@code input}.
     *
     * @param source        Specifies source of the stream.
     *                      Some StreamFormatters may only work with a pre-defined source or set of sources.
     *                      In this case, {@code source} must be one of these sources or {@code null}.
     * @param sourceStream  Stream to open. MUST belong to {@code source}.
     * @param destFormat    Desired destination format, or {@code null} if no formatting is requested.
     * @param sink          Sink to receive processed packets.
     * @return StreamHandle for newly created, formatted stream.
     * @throws ClosedChannelException if StreamFormatter is closed.
     * @throws IllegalArgumentException if source/sourceStream are invalid.
     * @throws IOException for most other failures.
     */
    public Stream openAudioStream( PacketReader source,
                                   Stream sourceStream,
                                   StreamFormat destFormat,
                                   Sink<? super DrawPacket> sink )
                                   throws IOException;

    public boolean closeStream( Stream stream ) throws IOException;

}
