package bits.drawjav.video;

import java.nio.ByteBuffer;
import java.util.*;

import bits.draw3d.DrawEnv;
import bits.draw3d.tex.Texture;
import bits.drawjav.*;
import bits.jav.Jav;
import static javax.media.opengl.GL3.*;


/**
 * Texture node that converts output of video stream to dynamic texture.
 *  
 * @author Philip DeCamp  
 */
public class VideoTexture implements Texture, Sink<VideoPacket> {

    private VideoPacket mNextFrame    = null;
    private VideoPacket mCurrentFrame = null;
    
    private final int[] mId = { 0 };
    private int mIntFormat = GL_RGBA;
    private int mFormat    = GL_RGBA;
    private int mDataType  = GL_UNSIGNED_BYTE;
    private int mWidth     = -1;
    private int mHeight    = -1;
    private int mDepth     = 1;
    
    private final Map<Integer,Integer> mParams = new HashMap<Integer,Integer>(4);
    
    private boolean mNeedUpdate = true;
    private boolean mNeedInit   = true;
    private boolean mDisposed   = false;
    
    private final int[] mRevert = { 0, 0 };
    
    
    public VideoTexture() {
        param( GL_TEXTURE_MIN_FILTER, GL_LINEAR );
        param( GL_TEXTURE_MAG_FILTER, GL_LINEAR );
    }

    
    //==========================
    // Sink methods
    //==========================
    
    public void consume( VideoPacket frame ) {
        if( frame == null || frame.javaBufElem( 0 ) == null ) {
            return;
        }

        PictureFormat format = frame.pictureFormat();
        if( format == null || format.width() <= 0 || format.height() <= 0 ) {
            return;
        }
        
        switch( format.pixelFormat() ) {
        case Jav.AV_PIX_FMT_BGR24:
        case Jav.AV_PIX_FMT_BGRA:
            break;
        default:
            return;
        }
    
        synchronized(this) {
            if( mDisposed ) {
                return;
            }
            
            frame.ref();
            if(mNextFrame != null) {
                mNextFrame.deref();
            }
            mNextFrame  = frame;
        }
    }
    
    
    public void clear() {}
    
    
    public void close() {}

    
    public boolean isOpen() {
        return !mDisposed;
    }
    
    
    //==========================
    // TextureNode methods
    //==========================
    
    public int target() {
        return GL_TEXTURE_2D;
    }
    
    public int id() {
        return mId[0];
    }
    
    public int internalFormat() {
        return mIntFormat;
    }
    
    public int format() {
        return mFormat;
    }
    
    public int dataType() {
        return mDataType;
    }
    
    public void size( int w, int h ) {
        mWidth  = w;
        mHeight = h;
    }
    
    
    public int width() {
        return mWidth;
    }
    
    public int height() {
        return mHeight;
    }
    
    public boolean hasSize() {
        return mWidth >= 0 && mHeight >= 0;
    }
    
    public void resizeOnReshape( boolean resizeOnReshape ) {}
    
    public boolean resizeOnReshape() {
        return false;
    }
    
    public void depth( int depth ) {}
    
    public int depth() {
        return mDepth;
    }
    
    public Integer param( int key ) {
        return mParams.get( key );
    }
    
    public void param( int key, int value ) {
        Integer prev = mParams.put( key, value );
        if( prev == null || prev != value ) {
            mNeedUpdate = true;
            mNeedInit   = true;
        }
    }
    
    public void format( int intFormat, int format, int dataType ) {}
    

    
    public void init( DrawEnv d ) {
        bind( d );
    }
    
    
    public synchronized void dispose( DrawEnv d ) {
        if( mDisposed ) {
            return;
        }
        
        mDisposed = true;
        mNeedUpdate = true;

        if( mId[0] != 0 ) {
            d.mGl.glDeleteTextures( 1, mId, 0 );
            mId[0] = 0;
        }
        
        if( mNextFrame != null ) {
            mNextFrame.deref();
            mNextFrame = null;
        }
        
        if( mCurrentFrame != null ) {
            mCurrentFrame.deref();
            mCurrentFrame = null;
        }
    }


    public void bind( DrawEnv d, int unit ) {
        d.mGl.glActiveTexture( unit );
        bind( d );
    }

    
    public void bind( DrawEnv d ) {
        if( mNeedUpdate && !doUpdate( d ) ) {
            return;
        }
        
        boolean buffer = queueBuffer();
        d.mGl.glBindTexture( GL_TEXTURE_2D, mId[0] );
        if( buffer ) {
            doBuffer( d );
        }
    }


    public void unbind( DrawEnv d, int unit ) {
        d.mGl.glActiveTexture( unit );
        unbind( d );
    }

    
    public void unbind( DrawEnv d ) {
        d.mGl.glBindTexture( GL_TEXTURE_2D, 0 );
    }


    public void reshape( DrawEnv d ) {}



    private boolean doUpdate( DrawEnv d ) {
        mNeedUpdate = false;
        
        if( mDisposed ) {
            return false;
        }
        
        if( mNeedInit ) {
            mNeedInit = false;
            
            if( mId[0] == 0 ) {
                d.mGl.glGenTextures( 1, mId, 0 );
                if( mId[0] <= 0 ) {
                    throw new RuntimeException( "Failed to allocate texture." );
                }
            }

            d.mGl.glBindTexture( GL_TEXTURE_2D, mId[0] );
            for( Map.Entry<Integer,Integer> e: mParams.entrySet() ) {
                d.mGl.glTexParameteri( GL_TEXTURE_2D, e.getKey(), e.getValue() );

            }
        }
        
        return true;
    }
    
    
    private synchronized boolean queueBuffer() {
        if( mNextFrame == null || mCurrentFrame == mNextFrame ) {
            return false;
        }
        
        mNextFrame.ref();
        if( mCurrentFrame != null ) {
            mCurrentFrame.deref();
        }
        mCurrentFrame  = mNextFrame;
        return true;
    }
    
    
    private void doBuffer( DrawEnv d ) {
        VideoPacket frame = mCurrentFrame;
        PictureFormat fmt = frame.pictureFormat();
        mWidth  = fmt.width();
        mHeight = fmt.height();

        if( fmt.pixelFormat() == Jav.AV_PIX_FMT_BGR24 ) {
            mFormat = GL_BGR;
        } else {
            mFormat = GL_BGRA;
        }

        int s = frame.lineSize( 0 );
        d.mGl.glPixelStorei( GL_PACK_ROW_LENGTH, s );
        //d.mGl.glTexImage2D( GL_TEXTURE_2D, 0, mIntFormat, mWidth, mHeight, 0, mFormat, mDataType, frame.directBuffer() );
        ByteBuffer bb = frame.javaBufElem( 0 );
        d.mGl.glTexImage2D( GL_TEXTURE_2D, 0, mIntFormat, mWidth, mHeight, 0, mFormat, mDataType, bb );
        d.mGl.glPixelStorei( GL_PACK_ROW_LENGTH, 0 );
    }

}
