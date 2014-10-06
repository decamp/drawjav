package bits.drawjav.video;

import bits.jav.Jav;
import bits.jav.codec.JavCodecContext;
import bits.jav.util.Rational;

import static bits.jav.Jav.*;


/**
 * Defines format of a single image.
 *
 * @author decamp
 */
public class PictureFormat {

    public static PictureFormat fromCodecContext( JavCodecContext cc ) {
        return new PictureFormat( cc.width(),
                                  cc.height(),
                                  cc.pixelFormat(),
                                  cc.sampleAspectRatio() );
    }


    private final int      mWidth;
    private final int      mHeight;
    private final int      mPixelFormat;
    private final Rational mSampleAspect;


    public PictureFormat() {
        this( -1, -1, AV_PIX_FMT_NONE, null );
    }

    /**
     * @param width       Width of picture, or -1 if undefined.
     * @param height      Height of picture, or -1 if undefined.
     * @param pixelFormat PixelFormat of picture, or -1 (PIX_FMT_NONE) if undefined.
     */
    public PictureFormat( int width, int height, int pixelFormat ) {
        this( width, height, pixelFormat, null );
    }

    /**
     * @param width        Width of picture, or -1 if undefined.
     * @param height       Height of picture, or -1 if undefined.
     * @param pixelFormat  PixelFormat of picture, or -1 (PIX_FMT_NONE) if undefined.
     * @param sampleAspect Aspect ratio of samples (pixels), or null if undefined.
     */
    public PictureFormat( int width, int height, int pixelFormat, Rational sampleAspect ) {
        mWidth = width >= 0 ? width : -1;
        mHeight = height >= 0 ? height : -1;
        mPixelFormat = pixelFormat;

        if( sampleAspect == null || sampleAspect.num() == 0 || sampleAspect.den() == 0 ) {
            mSampleAspect = null;
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
     * @return aspect ratio of samples (pixels), or null if undefined.
     */
    public Rational sampleAspect() {
        return mSampleAspect;
    }

    @Override
    public int hashCode() {
        return mWidth ^ (mHeight << 16) ^ (mHeight >>> 16) ^ mPixelFormat;
    }

    @Override
    public boolean equals( Object obj ) {
        if( !(obj instanceof PictureFormat) ) {
            return false;
        }
        PictureFormat p = (PictureFormat)obj;

        if( mWidth != p.mWidth ||
            mHeight != p.mHeight ||
            mPixelFormat != p.mPixelFormat )
        {
            return false;
        }

        return ( mSampleAspect == p.mSampleAspect ) || ( mSampleAspect != null ) && mSampleAspect.equals( p.mSampleAspect );
    }


    public String toString() {
        StringBuilder s = new StringBuilder( "PictureFormat [" );
        s.append( mWidth );
        s.append( " X " );
        s.append( mHeight );
        s.append( ", fmt: " );
        s.append( mPixelFormat );

        if( mSampleAspect != null ) {
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

        if( source.sampleAspect() != null && dest.sampleAspect() != null &&
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

        if( inAspect != null ) {
            if( outAspect != null ) {
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

        if( requested.sampleAspect() == null && inAspect != null ) {
            int anum  = inAspect.den() * inWidth * outHeight;
            int aden  = inAspect.num() * outWidth * inHeight;
            outAspect = Rational.reduce( anum, aden );
        }

        return new PictureFormat( outWidth, outHeight, outFmt, outAspect );
    }

}
