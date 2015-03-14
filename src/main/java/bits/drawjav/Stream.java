/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

/**
 * Represents a single stream of audio or video.
 *
 * @author decamp
 */
public interface Stream {

    /**
     * @return format if {@code type() == Jav.AV_MEDIA_TYPE_VIDEO}. Otherwise, null.
     */
    public StreamFormat format();

}
