package bits.drawjav;

import bits.jav.Jav;
import bits.jav.util.Rational;


/**
 * @author decamp
 */
public final class PictureFormats {
    
    
    public static boolean isCompatible( PictureFormat source, PictureFormat dest ) {
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
    

    public static boolean isFullyDefined( PictureFormat format ) {
        if( format == null ) {
            return false;
        }
        return format.width() >= 0 && 
               format.height() >= 0 && 
               format.pixelFormat() != Jav.AV_PIX_FMT_NONE;
    }

    
    public static PictureFormat merge( PictureFormat fmt, PictureFormat req ) {
        return merge( fmt, 0, 0, req );
    }
    
    
    public static PictureFormat merge( PictureFormat fmt, 
                                       int cropTop,
                                       int cropBottom,
                                       PictureFormat req ) 
    {
        PictureFormat format = null;
        
        if( fmt == null ) {
            if( req == null ) {
                return new PictureFormat( -1, -1, Jav.AV_PIX_FMT_NONE );
            }
            
            return req;
        }
        
        if( req == null ) {
            int inHeight = Math.max( 0, fmt.height() - cropTop - cropBottom );
            if( inHeight == fmt.height() ) {
                return fmt;
            }
            return new PictureFormat( fmt.width(), inHeight, fmt.pixelFormat(), fmt.sampleAspect() );
        }
        
        int inWidth        = fmt.width();
        int inHeight       = Math.max( 0, fmt.height() - cropTop - cropBottom );
        int inFmt          = fmt.pixelFormat();
        Rational inAspect  = fmt.sampleAspect();
        int outWidth       = req.width();
        int outHeight      = req.height();
        int outFmt         = req.pixelFormat();
        Rational outAspect = req.sampleAspect();
        
        double aspectScale = 1.0;

        if( inAspect != null ) {
            if( outAspect != null ) {
                aspectScale = outAspect.asDouble() / inAspect.asDouble();
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

        if( req.sampleAspect() == null && inAspect != null ) {
            int anum  = inAspect.den() * inWidth * outHeight;
            int aden  = inAspect.num() * outWidth * inHeight;
            outAspect = Rational.reduce( anum, aden );
        }
        
        return new PictureFormat( outWidth, outHeight, outFmt, outAspect );
    }



    private PictureFormats() {}
    
}
