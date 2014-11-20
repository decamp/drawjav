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
