package cogmac.javdraw.video;

import cogmac.jav.JavConstants;
import cogmac.javdraw.*;
import cogmac.draw3d.nodes.*;

import javax.media.opengl.GL;
import static javax.media.opengl.GL.*;


/**
 * Texture node that converts output of video stream to dynamic texture.
 *  
 * @author Philip DeCamp  
 */
public class VideoTexture extends DrawNodeAdapter implements Sink<VideoPacket> {

    private final Texture2dNode mTex;
    
    private VideoPacket mNextFrame    = null;
    private VideoPacket mCurrentFrame = null;
    
    
    public VideoTexture() {
        mTex = Texture2dNode.newInstance();
    }
    
    
    
    public void consume(VideoPacket frame) {
        if(frame == null || !frame.hasDirectBuffer())
            return;
        
        PictureFormat format = frame.pictureFormat();
        
        if(format == null || format.width() <= 0 || format.height() <= 0)
            return;
        
        switch(format.pixelFormat()) {
        case JavConstants.PIX_FMT_BGR24:
        case JavConstants.PIX_FMT_BGRA:
            break;
            
        default:
            return;
        }
    
        synchronized(this) {
            frame.ref();

            if(mNextFrame != null) {
                mNextFrame.deref();
            }

            mNextFrame = frame;
        }
    }
    
    
    public void clear() {}
    
    
    public void close() {}
    
    
    
    @Override
    public void pushDraw(GL gl) {
        boolean load = false;
        
        synchronized(this) {
            if(mNextFrame != null && mCurrentFrame != mNextFrame) {
                load = true;
                    
                mNextFrame.ref();
                
                if(mCurrentFrame != null) {
                    mCurrentFrame.deref();
                }
                
                mCurrentFrame = mNextFrame;
            }
        }
        
        
        
        mTex.pushDraw(gl);
        
        if(load) {
            VideoPacket frame  = mCurrentFrame;
            PictureFormat f = frame.pictureFormat();
            
            int t;
            
            if(f.pixelFormat() == JavConstants.PIX_FMT_BGR24) {
                t = GL_BGR;
            }else{
                t = GL_BGRA;
            }
            
            int s = frame.lineSize(0);
            
            gl.glPixelStorei(GL_PACK_ROW_LENGTH, s);
            gl.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, f.width(), f.height(), 0, t, GL_UNSIGNED_BYTE, frame.directBuffer());
            gl.glPixelStorei(GL_PACK_ROW_LENGTH, 0);
        }
    }
    

    @Override
    public void popDraw(GL gl) {
        mTex.popDraw(gl);
    }



}
