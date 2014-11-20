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



/**
 * @author decamp
 */
public interface StreamFormatter {
    
    public StreamHandle openVideoStream( StreamHandle source,
                                         PictureFormat dstFormat,
                                         Sink<? super VideoPacket> sink )
                                         throws IOException;

    public StreamHandle openAudioStream( StreamHandle source,
                                         AudioFormat dstFormat,
                                         Sink<? super AudioPacket> sink )
                                         throws IOException;

    public boolean closeStream( StreamHandle stream ) throws IOException;

}
