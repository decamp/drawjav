/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import bits.jav.codec.JavCodecContext;
import bits.jav.codec.JavFrame;
import bits.jav.util.JavChannelLayout;
import bits.jav.util.JavSampleFormat;

import static bits.jav.Jav.*;

/**
 * Describes the format of an audio stream, including the number of audio channels,
 * the sample rate, type of samples, and layout of channels. Any of these values
 * may also be left undefined, which can be useful for partial descriptions. For example,
 * you may need an audio stream that has only one channel, but where the sample rate
 * doesn't matter.
 *
 * @author decamp
 */
public class AudioFormat {

    public static AudioFormat fromCodecContext( JavCodecContext cc ) {
        AudioFormat ret = new AudioFormat( cc.channels(),
                                           cc.sampleRate(),
                                           cc.sampleFormat(),
                                           cc.channelLayout() );
        return ret;
    }


    public static AudioFormat fromPacket( JavFrame frame ) {
        AudioFormat ret = new AudioFormat( frame.channels(),
                                           frame.sampleRate(),
                                           frame.format(),
                                           frame.channelLayout() );
        return ret;
    }


    public final int  mChannels;
    public final int  mSampleRate;
    public final int  mSampleFormat;
    public final long mChannelLayout;


    public AudioFormat() {
        mChannels = -1;
        mSampleRate = -1;
        mSampleFormat = AV_SAMPLE_FMT_NONE;
        mChannelLayout = AV_CH_LAYOUT_NATIVE;
    }

    /**
     * @param channels      Number of channels, or -1 if undefined.
     * @param sampleRate    Sample rate, or -1 if undefined.
     * @param sampleFormat  Sample format, or AV_SAMPLE_FMT_NONE if undefined.
     */
    public AudioFormat( int channels, int sampleRate, int sampleFormat )
    {
        this( channels,
              sampleRate,
              sampleFormat,
              channels <= 0 ? AV_CH_LAYOUT_NATIVE : JavChannelLayout.getDefault( channels ) );
    }

    /**
     * @param channels      Number of channels, or -1 if undefined.
     * @param sampleRate    Sample rate, or -1 if undefined.
     * @param sampleFormat  Sample format, or AV_SAMPLE_FMT_NONE if undefined.
     * @param layout        Channel layout, or AV_CH_LAYOUT_NATIVE if undefined.
     */
    public AudioFormat( int channels, int sampleRate, int sampleFormat, long layout )
    {
        mChannels = channels >= 0 ? channels : -1;
        mSampleRate = sampleRate >= 0 ? sampleRate : -1;
        mSampleFormat = sampleFormat;
        mChannelLayout = layout;
    }


    public boolean matches( JavFrame frame ) {
        return mChannels == frame.channels() &&
               mSampleRate == frame.sampleRate() &&
               mSampleFormat == frame.format() &&
               mChannelLayout == frame.channelLayout();
    }



    @Override
    public boolean equals( Object o ) {
        if( !(o instanceof AudioFormat) ) {
            return false;
        }

        AudioFormat f = (AudioFormat)o;
        return mChannels == f.mChannels &&
               mSampleRate == f.mSampleRate &&
               mSampleFormat == f.mSampleFormat &&
               mChannelLayout == f.mChannelLayout;
    }

    @Override
    public int hashCode() {
        return mChannels ^ mSampleRate ^ mSampleFormat ^ (int)(mChannelLayout);
    }

    @Override
    public String toString() {
        return "AudioFormat [ " +
               "chans: " + mChannels + "/" + JavChannelLayout.getString( mChannels, mChannelLayout ) + ", " +
               "rate: " + mSampleRate + ", " +
               "fmt: " + JavSampleFormat.getName( mSampleFormat ) + " (" + mSampleFormat + ")]";
    }


    /**
     * An AudioFormat is fully defined if it contains enough information to
     * describe actual audio data and can be used to allocate buffers. This requires
     * that {@link #mChannels}, {@link #mSampleRate}, and {@link #mSampleFormat}
     * are defined. Note thate {@link #mChannelLayout need not be defined as a
     * default channel layout can easily be inferred from the channel number.
     *
     * @return true iff this AudioFormat is fully defined.
     */
    public static boolean isFullyDefined( AudioFormat f ) {
        return f != null &&
               f.mChannels >= 0 &&
               f.mSampleRate >= 0 &&
               f.mSampleFormat != AV_SAMPLE_FMT_NONE;
    }

    /**
     * Determines if the packets of format {@code src} can be passed directly to a destination
     * of format {@code dst} without conversion.
     *
     * @param src Format of source. May be partially defined or null.
     * @param dst Format of destination. May be partially defined or null.
     * @return true iff data from src can be passed directly to dst.
     */
    public static boolean areCompatible( AudioFormat src, AudioFormat dst ) {
        if( src == null || dst == null ) {
            return true;
        }

        // Check channel nums.
        if( src.mChannels > 0 &&
            dst.mChannels > 0 &&
            src.mChannels != dst.mChannels )
        {
            return false;
        }

        // Check sample format.
        if( src.mSampleFormat != AV_SAMPLE_FMT_NONE &&
            dst.mSampleFormat != AV_SAMPLE_FMT_NONE &&
            src.mSampleFormat != dst.mSampleFormat )
        {
            return false;
        }

        // Check sample rate.
        if( src.mSampleRate > 0 &&
            dst.mSampleRate > 0 &&
            src.mSampleRate != dst.mSampleRate )
        {
            return false;
        }

        // Check channel layout
        if( src.mChannelLayout != AV_CH_LAYOUT_NATIVE &&
            dst.mChannelLayout != AV_CH_LAYOUT_NATIVE &&
            src.mChannelLayout != dst.mChannelLayout )
        {
            return false;
        }

        return true;
    }

    /**
     * Merging will fill in as many undefined values in {@code requested} with
     * values from {@code src} as possible.
     *
     * @param source     The base AudioFormat of a stream. May be partially defined or null.
     * @param requested  The AudioFormat requested later in the stream. May be partially defined or null.
     * @return the most complete version of requested format as possible
     */
    public static AudioFormat merge( AudioFormat source, AudioFormat requested ) {
        if( source == null  ) {
            return requested == null ? new AudioFormat() : requested;
        }

        if( requested == null ) {
            return source;
        }

        int srcChans = source.mChannels;
        int chans = requested.mChannels;
        long layout = requested.mChannelLayout;
        if( chans < 0 ) {
            chans = srcChans;
            if( layout == AV_CH_LAYOUT_NATIVE ) {
                layout = source.mChannelLayout;
            }
        }

        int rate = requested.mSampleRate;
        if( rate < 0 ) {
            rate = source.mSampleRate;
        }
        int fmt = requested.mSampleFormat;
        if( fmt == AV_SAMPLE_FMT_NONE ) {
            fmt = source.mSampleFormat;
        }

        return new AudioFormat( chans, rate, fmt, layout );
    }

}
