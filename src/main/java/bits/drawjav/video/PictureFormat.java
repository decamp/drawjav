/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.video;

import bits.jav.Jav;
import bits.jav.codec.JavCodecContext;
import bits.jav.codec.JavFrame;
import bits.jav.util.Rational;

import static bits.jav.Jav.*;


/**
 * Defines format of a single image.
 *
 * @author decamp
 */
public class PictureFormat {

    private static final Rational ZERO = new Rational( 0, 1 );


    public static PictureFormat fromCodecContext( JavCodecContext cc ) {
        PictureFormat ret = new PictureFormat();
        ret.set( cc );
        return ret;
    }


    public static PictureFormat fromPacket( JavFrame frame ) {
        PictureFormat ret = new PictureFormat();
        ret.set( frame );
        return ret;
    }


    public int      mWidth;
    public int      mHeight;
    public int      mPixelFormat;
    public Rational mSampleAspect;


    public PictureFormat() {
        this( -1, -1, AV_PIX_FMT_NONE, ZERO );
    }

    /**
     * @param width       Width of picture, or -1 if undefined.
     * @param height      Height of picture, or -1 if undefined.
     * @param pixelFormat PixelFormat of picture, or -1 (PIX_FMT_NONE) if undefined.
     */
    public PictureFormat( int width, int height, int pixelFormat ) {
        this( width, height, pixelFormat, ZERO );
    }

    /**
     * @param width        Width of picture, or -1 if undefined.
     * @param height       Height of picture, or -1 if undefined.
     * @param pixelFormat  PixelFormat of picture, or -1 (PIX_FMT_NONE) if undefined.
     * @param sampleAspect Aspect ratio of samples (pixels), {@code null or 0/1} if undefined.
     */
    public PictureFormat( int width, int height, int pixelFormat, Rational sampleAspect ) {
        mWidth = width >= 0 ? width : -1;
        mHeight = height >= 0 ? height : -1;
        mPixelFormat = pixelFormat;
        if( sampleAspect == null || sampleAspect.num() == 0 || sampleAspect.den() == 0 ) {
            mSampleAspect = ZERO;
        } else {
            mSampleAspect = sampleAspect;
        }
    }


    /**
     * @return picture width in pixels, or -1 if undefined.
     */
    public int width() {
        return mWidth;
    }

    /**
     * @return picture height in pixels, or -1 if undefined.
     */
    public int height() {
        return mHeight;
    }

    /**
     * @return true iff width() and height() are both defined and thus non-negative.
     */
    public boolean hasSize() {
        return mWidth >= 0 && mHeight >= 0;
    }

    /**
     * @return PIX_FMT value, or -1 (PIX_FMT_NONE) if undefined.
     */
    public int pixelFormat() {
        return mPixelFormat;
    }

    /**
     * @return aspect ratio of samples (pixels). {@code 0/1} if undefined.
     */
    public Rational sampleAspect() {
        return mSampleAspect;
    }


    public void set( JavCodecContext cc ) {
        mWidth = cc.width();
        mHeight = cc.height();
        mPixelFormat = cc.pixelFormat();
        mSampleAspect = cc.sampleAspectRatio();
    }


    public void set( JavFrame frame ) {
        mWidth = frame.width();
        mHeight = frame.height();
        mPixelFormat = frame.format();
        mSampleAspect = frame.sampleAspectRatio();
    }


    public boolean matches( JavFrame frame ) {
        return mWidth == frame.width() &&
               mHeight == frame.height() &&
               mPixelFormat == frame.format() &&
               mSampleAspect.equals( frame.sampleAspectRatio() );
    }


    @Override
    public int hashCode() {
        return mWidth ^ (mHeight << 16) ^ (mHeight >>> 16) ^ mPixelFormat ^ mSampleAspect.hashCode();
    }

    @Override
    public boolean equals( Object obj ) {
        if( !(obj instanceof PictureFormat) ) {
            return false;
        }
        PictureFormat p = (PictureFormat)obj;
        return this == p ||
               mWidth == p.mWidth &&
               mHeight == p.mHeight &&
               mPixelFormat == p.mPixelFormat &&
               mSampleAspect.equals( p.sampleAspect() );
    }


    public String toString() {
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
    }


    /**
     * A VideoFormat is fully defined if it contains enough information to
     * describe actual video data and can be used to allocate buffers. This requires
     * that {@link #width()}, {@link #height()}, and {@link #pixelFormat()}
     * are defined. Note thate {@link #sampleAspect() need not be defined.
     *
     * @return true iff this PictureFormat is fully defined.
     */
    public static boolean isFullyDefined( PictureFormat format ) {
        if( format == null ) {
            return false;
        }
        return format.width() >= 0 &&
               format.height() >= 0 &&
               format.pixelFormat() != Jav.AV_PIX_FMT_NONE;
    }

    /**
     * Determines if the packets of format {@code src} can be passed directly to a destination
     * of format {@code dst} without conversion.
     *
     * @param source Format of source. May be partially defined or null.
     * @param dest Format of destination. May be partially defined or null.
     * @return true iff data from src can be passed directly to dst.
     */
    public static boolean areCompatible( PictureFormat source, PictureFormat dest ) {
        if( source == null || dest == null ) {
            return true;
        }

        if( source.width() > 0 && dest.width() > 0 && source.width() != dest.width() ) {
            return false;
        }

        if( source.height() > 0 && dest.height() > 0 && source.height() != dest.height() ) {
            return false;
        }

        if( source.pixelFormat() != Jav.AV_PIX_FMT_NONE &&
            dest.pixelFormat() != Jav.AV_PIX_FMT_NONE &&
            source.pixelFormat() != dest.pixelFormat() )
        {
            return false;
        }

        if( source.sampleAspect().num() != 0 &&
            dest.sampleAspect().num() != 0 &&
            !source.sampleAspect().equals( dest.sampleAspect() ) )
        {
            return false;
        }

        return true;
    }

    /**
     * Merging will fill in as many undefined values in {@code requested} with
     * values from {@code source} as possible.
     *
     * @param source     The source PictureFormat of a stream. May be partially defined or null.
     * @param requested  The PIctureFormat requested later in the stream. May be partially defined or null.
     * @return the most complete version of requested format as possible
     */
    public static PictureFormat merge( PictureFormat source, PictureFormat requested ) {
        if( source == null ) {
            return requested == null ? new PictureFormat() : requested;
        }

        if( requested == null ) {
            return source;
        }

        int inWidth        = source.width();
        int inHeight       = Math.max( 0, source.height() );
        int inFmt          = source.pixelFormat();
        Rational inAspect  = source.sampleAspect();
        int outWidth       = requested.width();
        int outHeight      = requested.height();
        int outFmt         = requested.pixelFormat();
        Rational outAspect = requested.sampleAspect();
        double aspectScale = 1.0;

        if( inAspect.num() != 0 ) {
            if( outAspect.num() != 0 ) {
                aspectScale = outAspect.toDouble() / inAspect.toDouble();
            } else {
                outAspect = inAspect;
            }
        }

        //Pixel format defaults to input.
        if( outFmt == Jav.AV_PIX_FMT_NONE ) {
            outFmt = inFmt;
        }

        //Check if output size is defined, or input size is not defined.
        if( ( outWidth >= 0 && outHeight >= 0 ) || ( inWidth <= 0 || inHeight <= 0 ) ) {
            //Nothing to do.
        } else if( outWidth >= 0 ) {
            //Determine height from defined factors.
            double aspect = ((double)inWidth / inHeight) * aspectScale;
            outHeight = (int)Math.round( outWidth / aspect );
        } else if( outHeight >= 0 ) {
            //Determine width from defined factors.
            double aspect = ((double)inWidth / inHeight) * aspectScale;
            outWidth = (int)Math.round( outHeight * aspect );
        } else {
            //Determine width and height from defined factors.
            if( aspectScale > 1.0 ) {
                outWidth  = inWidth;
                outHeight = (int)Math.round( inHeight * aspectScale );
            } else if( aspectScale < 1.0 ) {
                outWidth  = (int)Math.round( inWidth / aspectScale );
                outHeight = inHeight;
            } else {
                outWidth  = inWidth;
                outHeight = inHeight;
            }
        }

        if( inWidth == 0 || inHeight == 0 || outWidth == 0 || outHeight == 0 ) {
            outWidth  = 0;
            outHeight = 0;
        }

        if( requested.sampleAspect().num() == 0 && inAspect.num() != 0 ) {
            int anum  = inAspect.den() * inWidth * outHeight;
            int aden  = inAspect.num() * outWidth * inHeight;
            outAspect = Rational.reduce( anum, aden );
        }

        return new PictureFormat( outWidth, outHeight, outFmt, outAspect );
    }

}
