package cogmac.javdraw;

import cogmac.data.Guid;


/**
 * @author decamp
 */
public interface StreamHandle {
    public Guid guid();
    
    /**
     * Types are defined in JavConstants.AV_MEDIA_TYPE_?
     * @return media type of stream.
     */
    public int type();
    public PictureFormat pictureFormat();
    public AudioFormat audioFormat();
}
