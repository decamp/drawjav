/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import bits.drawjav.audio.AudioFormat;
import bits.drawjav.video.PictureFormat;
import bits.util.Guid;

/**
 * @author decamp
 */
public interface StreamHandle {

    /**
     * @return Unique identifier for this stream.
     */
    public Guid guid();
    
    /**
     * Types are defined in Jav.AV_MEDIA_TYPE_?
     * @return media type of stream.
     */
    public int type();

    /**
     * @return format if {@code type() == Jav.AV_MEDIA_TYPE_VIDEO}. Otherwise, null.
     */
    public PictureFormat pictureFormat();

    /**
     * @return format if {@code type() == Jav.AV_MEDIA_TYPE_AUDIO}. Otherwise, null.
     */
    public AudioFormat audioFormat();
}
