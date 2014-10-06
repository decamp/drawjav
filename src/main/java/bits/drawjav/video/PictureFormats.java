package bits.drawjav.video;

import bits.jav.Jav;
import bits.jav.util.Rational;


/**
 * @author decamp
 * @deprecated Just use {@link PictureFormat}
 */
public final class PictureFormats {
    
    
    public static boolean isCompatible( PictureFormat source, PictureFormat dest ) {
        return PictureFormat.areCompatible( source, dest );
    }
    

    public static boolean isFullyDefined( PictureFormat format ) {
        return PictureFormat.isFullyDefined( format );
    }

    
    public static PictureFormat merge( PictureFormat fmt, PictureFormat req ) {
        return PictureFormat.merge( fmt, req );
    }



    private PictureFormats() {}
    
}
