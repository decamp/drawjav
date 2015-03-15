/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import bits.drawjav.audio.AudioAllocator;
import bits.drawjav.video.VideoAllocator;

/**
 * @author Philip DeCamp
 */
public interface MemoryManager {
    public VideoAllocator videoAllocator( StreamFormat stream );
    public AudioAllocator audioAllocator( StreamFormat stream );
}
