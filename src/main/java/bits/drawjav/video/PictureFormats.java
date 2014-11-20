/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.video;

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
