package bits.drawjav;

import bits.drawjav.audio.AudioAllocator;
import bits.drawjav.audio.AudioFormat;
import bits.drawjav.video.PictureFormat;
import bits.drawjav.video.VideoAllocator;


/**
 * @author Philip DeCamp
 */
public interface MemoryManager {
    public VideoAllocator videoAllocator( StreamHandle stream, PictureFormat format );
    public AudioAllocator audioAllocator( StreamHandle stream, AudioFormat format   );
}
