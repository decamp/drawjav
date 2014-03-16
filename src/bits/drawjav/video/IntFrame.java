package bits.drawjav.video;

import bits.util.ref.*;

/**
 * Buffered format for video frame that can be easily modified.
 * 
 * @author decamp
 */
public class IntFrame extends AbstractRefable {
    
    private final ObjectPool<? super IntFrame> mPool;
    
    public int[] mPix;
    public int mWidth;
    public int mHeight;
    public int mStride;
    

    public IntFrame( ObjectPool<? super IntFrame> pool ) {
        mPool = pool;
    }


    public IntFrame( ObjectPool<? super IntFrame> pool, int w, int h ) {
        this( pool, new int[w * h], w, h, w );
    }


    public IntFrame( ObjectPool<? super IntFrame> pool, int[] pix, int w, int h, int s ) {
        mPool = pool;
        mPix = pix;
        mWidth = w;
        mHeight = h;
        mStride = s;
    }



    public void resize( int w, int h, int s ) {
        mWidth = w;
        mHeight = h;
        mStride = s;

        if( mPix == null || mPix.length < h * s ) {
            mPix = new int[h * s];
        }
    }


    protected void freeObject() {
        if( mPool != null ) {
            mPool.offer( this );
        }
    }
    
}
