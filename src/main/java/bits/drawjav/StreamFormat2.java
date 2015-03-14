/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import static bits.jav.Jav.*;

import bits.jav.Jav;
import bits.jav.codec.JavCodecContext;
import bits.jav.codec.JavFrame;
import bits.jav.util.*;
import com.google.common.base.Objects;

/**
 * Represents the format of a stream.
 *
 * @author decamp
 */
public class StreamFormat2 {

    public static final int      NO_CHANNELS      = 0;
    public static final int      NO_SAMPLE_RATE   = 0;
    public static final int      NO_WIDTH         = 0;
    public static final int      NO_HEIGHT        = 0;
    public static final Rational NO_SAMPLE_ASPECT = new Rational( 0, 0 );


    public static StreamFormat2 createAudio( int channels, int sampleRate, int sampleFormat ) {
        return new StreamFormat2( AVMEDIA_TYPE_AUDIO,
                                  channels,
                                  sampleRate,
                                  sampleFormat,
                                  AV_CH_LAYOUT_NATIVE,
                                  NO_WIDTH,
                                  NO_HEIGHT,
                                  AV_PIX_FMT_NONE,
                                  NO_SAMPLE_ASPECT );
    }


    public static StreamFormat2 createAudio( int channels, int sampleRate, int sampleFormat, long chanLayout ) {
        return new StreamFormat2( AVMEDIA_TYPE_AUDIO,
                                 channels,
                                 sampleRate,
                                 sampleFormat,
                                 chanLayout,
                                 NO_WIDTH,
                                 NO_HEIGHT,
                                 AV_PIX_FMT_NONE,
                                 NO_SAMPLE_ASPECT );
    }


    public static StreamFormat2 createAudio( JavFrame packet ) {
        return new StreamFormat2( AVMEDIA_TYPE_AUDIO,
                                  packet.channels(),
                                  packet.sampleRate(),
                                  packet.format(),
                                  packet.channelLayout(),
                                  NO_WIDTH,
                                  NO_HEIGHT,
                                  AV_PIX_FMT_NONE,
                                  NO_SAMPLE_ASPECT );
    }


    public static StreamFormat2 createVideo( int w, int h, int format, Rational aspect ) {
        return new StreamFormat2( AVMEDIA_TYPE_VIDEO,
                                  NO_CHANNELS,
                                  NO_SAMPLE_RATE,
                                  AV_SAMPLE_FMT_NONE,
                                  AV_CH_LAYOUT_NATIVE,
                                  w,
                                  h,
                                  format,
                                  aspect == null ? NO_SAMPLE_ASPECT : aspect );
    }


    public static StreamFormat2 createVideo( JavFrame packet ) {
        return new StreamFormat2( AVMEDIA_TYPE_AUDIO,
                                  NO_CHANNELS,
                                  NO_SAMPLE_RATE,
                                  AV_SAMPLE_FMT_NONE,
                                  AV_CH_LAYOUT_NATIVE,
                                  packet.width(),
                                  packet.height(),
                                  packet.format(),
                                  packet.sampleAspectRatio() );
    }

    /**
     * Merging will fill in as many undefined values in {@code requestedFormat} with
     * values from {@code sourceFormat} as possible.
     *
     * @param source     The sourceFormat format of a stream. May be partially defined or null.
     * @param requested  The requestedFormat format later in the stream. May be partially defined or null.
     * @return the most complete version of requestedFormat format as possible.
     *         A newly allocated StreamFormat object is always returned.
     */
    public static StreamFormat2 merge( StreamFormat2 source, StreamFormat2 requested ) {
        if( source == null ) {
            return requested == null ? new StreamFormat2() : requested;
        }

        if( requested == null ) {
            return source;
        }

        int      outType          = requested.mType != AVMEDIA_TYPE_UNKNOWN ? requested.mType : source.mType;
        int      outChannels      = NO_CHANNELS;
        int      outSampleRate    = NO_SAMPLE_RATE;
        int      outSampleFormat  = AV_SAMPLE_FMT_NONE;
        long     outChannelLayout = AV_CH_LAYOUT_NATIVE;
        int      outWidth         = NO_WIDTH;
        int      outHeight        = NO_HEIGHT;
        int      outPixelFormat   = AV_PIX_FMT_NONE;
        Rational outSampleAspect  = NO_SAMPLE_ASPECT;

        switch( outType ) {
        case AVMEDIA_TYPE_VIDEO: {
            outPixelFormat = requested.mPixelFormat != AV_PIX_FMT_NONE ? requested.mPixelFormat : source.mPixelFormat;
            outWidth  = requested.mWidth;
            outHeight = requested.mHeight;
            outSampleAspect = requested.mSampleAspect;
            double aspectScale = 1.0;

            if( source.mSampleAspect.num() != 0 ) {
                if( outSampleAspect.num() != 0 ) {
                    aspectScale = outSampleAspect.toDouble() / source.mSampleAspect.toDouble();
                } else {
                    outSampleAspect = source.mSampleAspect;
                }
            }

            //Check if output size is defined, or sourceFormat size is not defined.
            if( (outWidth > NO_WIDTH && outHeight > NO_HEIGHT) || (source.mWidth <= NO_WIDTH || source.mHeight <= NO_HEIGHT) ) {
                //Nothing to do.

            } else if( outWidth > NO_WIDTH ) {
                //Determine height from defined factors.
                double aspect = ((double)source.mWidth / source.mHeight) * aspectScale;
                outHeight = (int)Math.round( outWidth / aspect );

            } else if( outHeight > NO_WIDTH ) {
                //Determine width from defined factors.
                double aspect = ((double)source.mWidth / source.mHeight) * aspectScale;
                outWidth = (int)Math.round( outHeight * aspect );

            } else {
                //Determine width and height from defined factors.
                if( aspectScale > 1.0 ) {
                    outWidth = source.mWidth;
                    outHeight = (int)Math.round( source.mHeight * aspectScale );
                } else if( aspectScale < 1.0 ) {
                    outWidth = (int)Math.round( source.mWidth / aspectScale );
                    outHeight = source.mHeight;
                } else {
                    outWidth = source.mWidth;
                    outHeight = source.mHeight;
                }
            }

            if( source.mWidth <= NO_WIDTH || source.mHeight <= NO_HEIGHT || outWidth <= NO_WIDTH || outHeight <= NO_HEIGHT ) {
                outWidth = NO_WIDTH;
                outHeight = NO_HEIGHT;
            }

            if( requested.mSampleAspect.den() == 0 && source.mSampleAspect.den() != 0 ) {
                int anum = source.mSampleAspect.den() * source.mWidth * outHeight;
                int aden = source.mSampleAspect.num() * outWidth * source.mHeight;
                outSampleAspect = Rational.reduce( anum, aden );
            }

            break;
        }

        case AVMEDIA_TYPE_AUDIO: {
            outSampleFormat  = requested.mSampleFormat != AV_SAMPLE_FMT_NONE ?
                               requested.mSampleFormat : source.mSampleFormat;
            outChannels      = requested.mChannels;
            outChannelLayout = requested.mChannelLayout;
            if( outChannels <= NO_CHANNELS ) {
                outChannels = source.mChannels;
                if( outChannelLayout == AV_CH_LAYOUT_NATIVE ) {
                    outChannelLayout = source.mChannelLayout;
                }
            }
            outSampleRate = requested.mSampleRate > NO_SAMPLE_RATE ? requested.mSampleRate : source.mSampleRate;
            break;
        }

        default: {

        }}

        return new StreamFormat2( outType,
                                 outChannels,
                                 outSampleRate,
                                 outSampleFormat,
                                 outChannelLayout,
                                 outWidth,
                                 outHeight,
                                 outPixelFormat,
                                 outSampleAspect );
    }

    /**
     * Determines if the packets of format {@code src} can be passed directly to a destination
     * of format {@code dst} without conversion.
     *
     * @param src Format of sourceFormat. May be partially defined or null.
     * @param dst   Format of destination. May be partially defined or null.
     * @return true iff data from src can be passed directly to dst.
     */
    public static boolean areCompatible( StreamFormat2 src, StreamFormat2 dst ) {
        if( src == dst || src == null || dst == null ) {
            return true;
        }

        if( src.mType != dst.mType ) {
            return false;
        }

        switch( src.mType ) {
        case Jav.AVMEDIA_TYPE_AUDIO: {
            // Check channel nums.
            if( src.mChannels > NO_CHANNELS &&
                dst.mChannels > NO_CHANNELS &&
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
            if( src.mSampleRate > NO_SAMPLE_RATE &&
                dst.mSampleRate > NO_SAMPLE_RATE &&
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

        case Jav.AVMEDIA_TYPE_VIDEO: {
            if( src.mWidth > NO_WIDTH && dst.mWidth > NO_WIDTH && src.mWidth != dst.mWidth ) {
                return false;
            }

            if( src.mHeight > NO_HEIGHT && dst.mHeight > NO_HEIGHT && src.mHeight != dst.mHeight ) {
                return false;
            }

            if( src.mPixelFormat != Jav.AV_PIX_FMT_NONE &&
                dst.mPixelFormat != Jav.AV_PIX_FMT_NONE &&
                src.mPixelFormat != dst.mPixelFormat )
            {
                return false;
            }

            return src.mSampleAspect.num() * dst.mSampleAspect.den() ==
                   src.mSampleAspect.den() * dst.mSampleAspect.num();
        }}

        return true;
    }



    public static StreamFormat2 fromCodecContext( JavCodecContext cc ) {
        int type = cc.codecType();
        switch( type ) {
        case AVMEDIA_TYPE_AUDIO:
            return createAudio( cc.sampleFormat(), cc.channels(), cc.sampleRate(), cc.channelLayout() );
        case AVMEDIA_TYPE_VIDEO:
            return createVideo( cc.pixelFormat(), cc.width(), cc.height(), cc.sampleAspectRatio() );
        }

        return new StreamFormat2( type );
    }




    /** Types of stream. For video, Jav.AV_MEDIA_TYPE_VIDEO. For audio, Jav.AV_MEDIA_TYPE_AUDIO. */
    public final int mType;

    /** For audio, number of channels. */
    public final int mChannels;

    /** For audio, Jav.AV_SAMPLE_FMT_*. If undefined, Jav.AV_SAMPLE_FMT_NONE. */
    public final int mSampleFormat;

    /** For audio, sample rate. */
    public final int mSampleRate;

    /** For audio, AudioLayout. */
    public final long mChannelLayout;

    /** For video, width of picture in pixels, or 0 if unknown. */
    public final int mWidth;

    /** For video, height of picture in pixels, or 0 if unknown. */
    public final int mHeight;

    /** For video, Jav.AV_PIX_FMT_*. If undefined, Jav.AV_PIX_FMT_NONE */
    public final int mPixelFormat;

    /** For video, aspect ratio of pixels, or NO_SAMPLE_ASPECT if unknown. MAY NOT BE NULL */
    public final Rational mSampleAspect;


    protected final int mHash;


    public StreamFormat2() {
        mType          = AVMEDIA_TYPE_UNKNOWN;
        mChannels      = NO_CHANNELS;
        mSampleRate    = NO_SAMPLE_RATE;
        mSampleFormat  = AV_SAMPLE_FMT_NONE;
        mChannelLayout = NO_CHANNELS;
        mWidth         = NO_WIDTH;
        mHeight        = NO_HEIGHT;
        mPixelFormat   = AV_PIX_FMT_NONE;
        mSampleAspect  = NO_SAMPLE_ASPECT;
        mHash          = 0;
    }


    public StreamFormat2( int type ) {
        this( type,
              NO_CHANNELS,
              NO_SAMPLE_RATE,
              AV_SAMPLE_FMT_NONE,
              NO_CHANNELS,
              NO_WIDTH,
              NO_HEIGHT,
              AV_PIX_FMT_NONE,
              NO_SAMPLE_ASPECT );
    }


    public StreamFormat2( int type,
                          int channels,
                          int sampleRate,
                          int sampleFormat,
                          long channelLayout,
                          int width,
                          int height,
                          int pixelFormat,
                          Rational sampleAspect )
    {
        mType = type;
        mChannels = channels;
        mSampleRate = sampleRate;
        mSampleFormat = sampleFormat;
        mChannelLayout = channelLayout;
        mWidth = width;
        mHeight = height;
        mPixelFormat = pixelFormat;
        mSampleAspect = sampleAspect;

        mHash = type << 24 ^
                channels << 20 ^
                sampleRate ^
                sampleFormat << 24 ^
                (int)channelLayout ^
                width << 16 ^
                height << 1 ^
                pixelFormat << 24 ^
                sampleAspect.hashCode();
    }


    public StreamFormat2( StreamFormat2 copy ) {
        this( copy.mType,
              copy.mChannels,
              copy.mSampleRate,
              copy.mSampleFormat,
              copy.mChannelLayout,
              copy.mWidth,
              copy.mHeight,
              copy.mPixelFormat,
              copy.mSampleAspect );
    }



    public void getProperties( JavFrame out ) {
        switch( mType ) {
        case Jav.AVMEDIA_TYPE_AUDIO:
            getAudioProperties( out );
            break;
        case Jav.AVMEDIA_TYPE_VIDEO:
            getVideoProperties( out );
            break;
        }
    }


    public void getAudioProperties( JavFrame out ) {
        out.channels( mChannels );
        out.sampleRate( mSampleRate );
        out.format( mSampleFormat );
        out.channelLayout( mChannelLayout );
    }


    public void getVideoProperties( JavFrame out ) {
        out.width( mWidth );
        out.height( mHeight );
        out.format( mPixelFormat );
        out.sampleAspectRatio( mSampleAspect );
    }


    public boolean matches( JavFrame frame ) {
        switch( mType ) {
        case AVMEDIA_TYPE_AUDIO:
            return mChannels      == frame.channels() &&
                   mSampleRate    == frame.sampleRate() &&
                   mSampleFormat  == frame.format() &&
                   mChannelLayout == frame.channelLayout();

        case AVMEDIA_TYPE_VIDEO:
            return mWidth       == frame.width() &&
                   mHeight      == frame.height() &&
                   mPixelFormat == frame.format() &&
                   mSampleAspect.equals( frame.sampleAspectRatio() );
        }
        return false;
    }

    /**
     * @return true iff width() and height() are both defined and thus non-negative.
     */
    public boolean hasDimensions() {
        return mWidth > NO_WIDTH && mHeight > NO_HEIGHT;
    }

    /**
     * A stream format is "fully defined" if it contains enough information
     * to describe data and allocated buffers.
     * <p>
     * For video streams, the format must define format, width and height. Note
     * that format.mSampleAspect need be left as null, in which case it
     * will be assumed to be 1/1.
     * <p>
     * For audio streams, the format must define format, channels, and
     * sampleRate.
     *
     * @return true iff StreamFormat is fully defined.
     */
    public boolean isFullyDefined() {
        switch( mType ) {
        case Jav.AVMEDIA_TYPE_VIDEO:
            return mWidth  > NO_WIDTH &&
                   mHeight > NO_HEIGHT &&
                   mPixelFormat != Jav.AV_PIX_FMT_NONE;

        case Jav.AVMEDIA_TYPE_AUDIO:
            return mChannels   > NO_CHANNELS &&
                   mSampleRate > NO_SAMPLE_RATE &&
                   mSampleFormat != AV_SAMPLE_FMT_NONE;
        }

        return false;
    }

    @Override
    public boolean equals( Object o ) {
        if( o == this ) {
            return true;
        }

        if( !(o instanceof StreamFormat2) ) {
            return false;
        }

        StreamFormat2 f = (StreamFormat2)o;
        return mHash == f.mHash &&
               mType == f.mType &&
               mSampleFormat == f.mType &&
               mChannels == f.mChannels &&
               mSampleRate == f.mSampleRate &&
               mChannelLayout == f.mChannelLayout &&
               mWidth == f.mWidth &&
               mHeight == f.mHeight &&
               mPixelFormat == f.mPixelFormat &&
               Objects.equal( mSampleAspect, f.mSampleAspect );
    }

    @Override
    public String toString() {
        switch( mType ) {
        case AVMEDIA_TYPE_AUDIO: {
            StringBuilder s = new StringBuilder( "AudioStream [ chans: " );
            s.append( mChannels ).append( "/" );
            s.append( JavChannelLayout.getString( mChannels, mChannelLayout ) );
            s.append( ", rate: " ).append( mSampleRate );
            s.append( ", fmt: " ).append( JavSampleFormat.getName( mSampleFormat ) );
            s.append( " (" ).append( mSampleFormat ).append( ")]" );
            return s.toString();
        }

        case AVMEDIA_TYPE_VIDEO: {
            StringBuilder s = new StringBuilder( "PictureFormat [" );
            s.append( mWidth );
            s.append( " X " );
            s.append( mHeight );
            s.append( ", fmt: " );
            s.append( mPixelFormat );

            if( mSampleAspect.num() != 0 ) {
                s.append( ", aspect: " );
                s.append( mSampleAspect.num() );
                s.append( "/" ).append( mSampleAspect.den() );
            }

            s.append( "]" );
            return s.toString();
        }}

        return "Stream [ type: " + mType + " ]";
    }

}
