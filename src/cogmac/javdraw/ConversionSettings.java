package cogmac.javdraw;

public class ConversionSettings {
    
    /**
     * How many rows to crop from top of frame before conversion.
     */
    public int mCropTop    = 0;
    
    /**
     * How many rows to crop from bottom of frame before conversion.
     */
    public int mCropBottom = 0;
    
    /**
     * How to perform conversion. EG, JavConstants.SWS_FAST_BILINEAR.
     */
    public int mSwsFlag    = 0; 

}
