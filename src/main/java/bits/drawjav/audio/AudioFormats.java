/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.audio;

/**
 * @author decamp
 * @deprecated Just use {@link AudioFormat}
 */
@Deprecated
public final class AudioFormats {


    public static boolean isCompatible( AudioFormat source, AudioFormat dest ) {
        return AudioFormat.areCompatible( source, dest );
    }

    /**
     * An AudioFormat is fully defined i
     *
     * @return true iff {@code format} contains sufficient information to describe actual audio data.
     *         This means number of channels, sample format, and sample rate must be defined.
     *         ChannelLayout may be AV_CH_LAYOUT_NATIVE as a standard channel layout can be
     */
    public static boolean isFullyDefined( AudioFormat format ) {
        return AudioFormat.isFullyDefined( format );
    }

    
    public static AudioFormat merge( AudioFormat source, AudioFormat dest ) {
        return AudioFormat.merge( source, dest );
    }


    private AudioFormats() {}
    
}
