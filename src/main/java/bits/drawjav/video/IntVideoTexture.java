package bits.drawjav.video;

import java.nio.*;

import bits.draw3d.DrawEnv;
import bits.draw3d.DrawNodeAdapter;
import bits.draw3d.tex.Texture2;
import bits.drawjav.Sink;

import static javax.media.opengl.GL2ES3.*;


/**
 * Texture node that uses most recently consumed IntFrame
 * as texture.
 *
 * @author Philip DeCamp
 */
public class IntVideoTexture extends DrawNodeAdapter implements Sink<IntFrame> {


    private final Texture2 mTex;

    private IntFrame   mNextFrame    = null;
    private IntFrame   mCurrentFrame = null;
    private ByteBuffer mBuf          = null;


    public IntVideoTexture() {
        mTex = new Texture2();
    }


    public void consume( IntFrame frame ) {
        if( frame == null ) {
            return;
        }

        frame.ref();

        synchronized( this ) {
            if( mNextFrame != null ) {
                mNextFrame.deref();
            }

            mNextFrame = frame;
        }
    }


    public void clear() {}


    public void close() {}


    public boolean isOpen() {
        return true;
    }

    @Override
    public void pushDraw( DrawEnv d ) {
        boolean load = false;

        synchronized( this ) {
            if( mNextFrame != null && mCurrentFrame != mNextFrame ) {
                load = true;
                mNextFrame.ref();

                if( mCurrentFrame != null ) {
                    mCurrentFrame.deref();
                }
                mCurrentFrame = mNextFrame;
            }
        }

        mTex.bind( d, 0 );

        if( load ) {
            final int w = mCurrentFrame.mWidth;
            final int h = mCurrentFrame.mHeight;

            if( mBuf == null || mBuf.capacity() < w * h * 4 ) {
                mBuf = ByteBuffer.allocateDirect( w * h * 4 );
                mBuf.order( ByteOrder.BIG_ENDIAN );
            }

            int[] pix = mCurrentFrame.mPix;
            mBuf.clear();

            for( int i = 0; i < w * h; i++ ) {
                mBuf.putInt( (pix[i] << 8) | (pix[i] >>> 24) );
            }

            mBuf.flip();
            d.mGl.glTexImage2D( GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, mBuf );
            d.mGl.glPixelStorei( GL_PACK_ROW_LENGTH, 0 );
        }
    }

    @Override
    public void popDraw( DrawEnv d ) {
        //mTex.popDraw( d );
    }

}
