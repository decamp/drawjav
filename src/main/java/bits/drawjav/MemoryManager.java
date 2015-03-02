/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import bits.drawjav.audio.AudioAllocator;
import bits.drawjav.audio.AudioFormat;
import bits.drawjav.video.PictureFormat;
import bits.drawjav.video.VideoAllocator;


/**
 * @author Philip DeCamp
 */
public interface MemoryManager {
    public VideoAllocator videoAllocator( StreamHandle stream );
    public AudioAllocator audioAllocator( StreamHandle stream );
}
