/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.audio;

import bits.jav.codec.JavCodecContext;
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
        return new AudioFormat( cc.channels(), cc.sampleRate(), cc.sampleFormat(), cc.channelLayout() );
    }


    private final int mChannels;
    private final int mSampleRate;
    private final int mSampleFormat;
    private final long mLayout;


    public AudioFormat() {
        mChannels     = -1;
        mSampleRate   = -1;
        mSampleFormat = AV_SAMPLE_FMT_NONE;
        mLayout       = AV_CH_LAYOUT_NATIVE;
    }

    /**
     * @param channels      Number of channels, or -1 if undefined.
     * @param sampleRate    Sample rate, or -1 if undefined.
     * @param sampleFormat  Sample format, or AV_SAMPLE_FMT_NONE if undefined.
     */
    public AudioFormat( int channels, int sampleRate, int sampleFormat ) {
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
    public AudioFormat( int channels, int sampleRate, int sampleFormat, long layout ) {
        mChannels = channels >= 0 ? channels : -1;
        mSampleRate = sampleRate >= 0 ? sampleRate : -1;
        mSampleFormat = sampleFormat;
        mLayout = layout;
    }


    /**
     * @return number of channels, or -1 if undefined.
     */
    public int channels() {
        return mChannels;
    }

    /**
     * @return sample rate in Hertz, or -1 if undefined.
     */
    public int sampleRate() {
        return mSampleRate;
    }

    /**
     * @return sample format as found in Jav.AV_SAMPLE_FMT_NONE to Jav.AV_SAMPLE_FMT_NB.
     */
    public int sampleFormat() {
        return mSampleFormat;
    }

    /**
     * @return channel layout, as described by Jav.AV_CH_LAYOUT_*.
     */
    public long channelLayout() {
        return mLayout;
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
               mLayout == f.mLayout;
    }

    @Override
    public int hashCode() {
        return mChannels ^ mSampleRate ^ mSampleFormat ^ (int)(mLayout);
    }

    @Override
    public String toString() {
        return "AudioFormat [ " +
               "chans: " + mChannels + "/" + JavChannelLayout.getString( mChannels, mLayout ) + ", " +
               "rate: " + mSampleRate + ", " +
               "fmt: " + JavSampleFormat.getName( mSampleFormat ) + " (" + mSampleFormat + ")]";
    }


    /**
     * An AudioFormat is fully defined if it contains enough information to
     * describe actual audio data and can be used to allocate buffers. This requires
     * that {@link #channels()}, {@link #sampleRate()}, and {@link #sampleFormat()}
     * are defined. Note thate {@link #channelLayout() need not be defined as a
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
        if( src.channels() > 0 &&
            dst.channels() > 0 &&
            src.channels() != dst.channels() )
        {
            return false;
        }

        // Check sample format.
        if( src.sampleFormat() != AV_SAMPLE_FMT_NONE &&
            dst.sampleFormat() != AV_SAMPLE_FMT_NONE &&
            src.sampleFormat() != dst.sampleFormat() )
        {
            return false;
        }

        // Check sample rate.
        if( src.sampleRate() > 0 &&
            dst.sampleRate() > 0 &&
            src.sampleRate() != dst.sampleRate() )
        {
            return false;
        }

        // Check channel layout
        if( src.channelLayout() != AV_CH_LAYOUT_NATIVE &&
            dst.channelLayout() != AV_CH_LAYOUT_NATIVE &&
            src.channelLayout() != dst.channelLayout() )
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


        int srcChans = source.channels();
        int chans = requested.channels();
        long layout = requested.channelLayout();
        if( chans < 0 ) {
            chans = srcChans;
            if( layout == AV_CH_LAYOUT_NATIVE ) {
                layout = source.channelLayout();
            }
        }

        int rate = requested.sampleRate();
        if( rate < 0 ) {
            rate = source.sampleRate();
        }
        int fmt = requested.sampleFormat();
        if( fmt == AV_SAMPLE_FMT_NONE ) {
            fmt = source.sampleFormat();
        }

        return new AudioFormat( chans, rate, fmt, layout );
    }

}
