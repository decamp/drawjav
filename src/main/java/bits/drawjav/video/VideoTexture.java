package bits.drawjav.video;

import java.util.*;
import javax.media.opengl.*;

import bits.draw3d.nodes.*;
import bits.drawjav.*;
import bits.jav.Jav;
import static javax.media.opengl.GL.*;



/**
 * Texture node that converts output of video stream to dynamic texture.
 *  
 * @author Philip DeCamp  
 */
public class VideoTexture implements TextureNode, Sink<VideoPacket> {

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
        if( frame == null || !frame.hasDirectBuffer() ) {
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
        
        if( prev == null || prev.intValue() != value ) {
            mNeedUpdate = true;
            mNeedInit   = true;
        }
    }
    
    public void format( int intFormat, int format, int dataType ) {}
    
    
    
    public void init( GL gl ) {
        pushDraw( gl );
        popDraw( gl );
    }
    
    
    public synchronized void dispose( GL gl ) {
        if( mDisposed ) {
            return;
        }
        
        mDisposed = true;
        mNeedUpdate = true;
        
        if( mId[0] != 0 ) {
            gl.glDeleteTextures( 1, mId, 0 );
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
    
    
    public void bind( GL gl ) {
        if( mNeedUpdate && !doUpdate( gl ) ) {
            return;
        }
        
        boolean buffer = queueBuffer();
        gl.glBindTexture( GL_TEXTURE_2D, mId[0] );
        if( buffer ) {
            doBuffer( gl );
        }
    }
    
    
    public void unbind( GL gl ) {
        gl.glBindTexture( GL_TEXTURE_2D, 0 );
    }
    
    
    @Override
    public void pushDraw( GL gl ) {
        gl.glGetIntegerv( GL_TEXTURE_2D, mRevert, 0 );
        gl.glGetIntegerv( GL_TEXTURE_BINDING_2D, mRevert, 1 );
        
        if( mNeedUpdate && !doUpdate( gl ) ) {
            return;
        }
        
        boolean buffer = queueBuffer();
        gl.glBindTexture( GL_TEXTURE_2D, mId[0] );
        gl.glEnable( GL_TEXTURE_2D );
        if( buffer ) {
            doBuffer( gl );
        }
    }
    
    
    public void popDraw( GL gl ) {
        if( mRevert[0] == 0 ) {
            gl.glDisable( GL_TEXTURE_2D );
        }
        gl.glBindTexture( GL_TEXTURE_2D, mRevert[1] );
    }

    
    
    public void init( GLAutoDrawable gld ) {
        init( gld.getGL() );
    }
    
    
    public void dispose( GLAutoDrawable gld ) {
        dispose( gld.getGL() );
    }
    
    
    public void reshape( GLAutoDrawable gld, int x, int y, int w, int h ) {}
    
    
    
    
    private boolean doUpdate( GL gl ) {
        mNeedUpdate = false;
        
        if( mDisposed ) {
            return false;
        }
        
        if( mNeedInit ) {
            mNeedInit = false;
            
            if( mId[0] == 0 ) {
                gl.glGenTextures( 1, mId, 0 );
                if( mId[0] <= 0 ) {
                    throw new RuntimeException( "Failed to allocate texture." );
                }
            }

            gl.glBindTexture( GL_TEXTURE_2D, mId[0] );
            
            for( Map.Entry<Integer,Integer> e: mParams.entrySet() ) {
                gl.glTexParameteri( GL_TEXTURE_2D, e.getKey(), e.getValue() );
                
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
    
    
    private void doBuffer( GL gl ) {
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
        gl.glPixelStorei( GL_PACK_ROW_LENGTH, s );
        gl.glTexImage2D( GL_TEXTURE_2D, 0, mIntFormat, mWidth, mHeight, 0, mFormat, mDataType, frame.directBuffer() );
        gl.glPixelStorei( GL_PACK_ROW_LENGTH, 0 );
    }
    
}
