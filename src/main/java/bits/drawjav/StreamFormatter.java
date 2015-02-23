/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import bits.drawjav.audio.AudioFormat;
import bits.drawjav.audio.AudioPacket;
import bits.drawjav.video.PictureFormat;
import bits.drawjav.video.VideoPacket;

import java.io.*;
import java.nio.channels.ClosedChannelException;


/**
 * @author decamp
 */
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
    public StreamHandle openVideoStream( PacketReader source,
                                         StreamHandle sourceStream,
                                         PictureFormat destFormat,
                                         Sink<? super VideoPacket> sink )
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
    public StreamHandle openAudioStream( PacketReader source,
                                         StreamHandle sourceStream,
                                         AudioFormat destFormat,
                                         Sink<? super AudioPacket> sink )
                                         throws IOException;

    public boolean closeStream( StreamHandle stream ) throws IOException;

}
