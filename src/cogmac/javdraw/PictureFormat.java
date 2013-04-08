package cogmac.javdraw;

import static cogmac.jav.JavConstants.*;
import cogmac.jav.codec.JavCodecContext;
import cogmac.jav.util.Rational;

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
    
    
    private final int mWidth;
    private final int mHeight;
    private final int mPixelFormat;
    private final Rational mSampleAspect; 
    
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
     * @param sampleAspect Aspect ratio of samples (pixels).
     */
    public PictureFormat( int width, int height, int pixelFormat, Rational sampleAspect ) {
        mWidth        = width >= 0 ? width : -1;
        mHeight       = height >= 0 ? height : -1;
        mPixelFormat  = pixelFormat;
        mSampleAspect = sampleAspect == null ? null : sampleAspect.reduce();
    }
    
    
    private PictureFormat() {
        this(-1, -1, PIX_FMT_NONE, null);
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
    public boolean equals( Object obj ) {
        if( !( obj instanceof PictureFormat ) ) {
            return false;
        }
        PictureFormat p = (PictureFormat)obj;
        if( mWidth != p.mWidth ||
            mHeight != p.mHeight ||
            mPixelFormat != p.mPixelFormat )
        {
            return false;
        }
        return (mSampleAspect == p.mSampleAspect || 
                mSampleAspect != null && mSampleAspect.equals( p.mSampleAspect ) );
    }
    
    
    @Override
    public int hashCode() {
        return mWidth ^ (mHeight << 16) ^ (mHeight >>> 16) ^ mPixelFormat;
    }

    
    public String toString() {
        StringBuilder s = new StringBuilder("PictureFormat [");
        s.append(mWidth);
        s.append(" X ");
        s.append(mHeight);
        s.append(", fmt: ");
        s.append(mPixelFormat);
        
        if(mSampleAspect != null) {
            s.append(", aspect: ");
            s.append(mSampleAspect.num());
            s.append("/").append(mSampleAspect.den());
        }
        
        s.append("]");
        return s.toString();
    }
    
}
