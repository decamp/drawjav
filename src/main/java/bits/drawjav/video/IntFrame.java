/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.video;

import bits.util.ref.*;

/**
 * Buffered format for video frame that can be easily modified.
 * 
 * @author decamp
 */
public class IntFrame extends AbstractRefable {
    
    public int[] mPix;
    public int mWidth;
    public int mHeight;
    public int mStride;
    

    public IntFrame( ObjectPool<? super IntFrame> pool ) {
        super( pool );
    }


    public IntFrame( ObjectPool<? super IntFrame> pool, int w, int h ) {
        this( pool, new int[w * h], w, h, w );
    }


    public IntFrame( ObjectPool<? super IntFrame> pool, int[] pix, int w, int h, int s ) {
        super( pool );
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


    protected void freeObject() {}
    
}
