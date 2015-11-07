package bits.drawjav.video;

import bits.draw3d.*;
import bits.draw3d.util.LimitAnimator;
import bits.microtime.Ticker;

import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLCanvas;
import javax.swing.*;

import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_TEXTURE0;


/**
 * @author Philip DeCamp
 */
public class VideoWindow extends JFrame implements GLEventListener {

    private final Ticker     mOptTicker;
    private final DrawUnit[] mTexs;
    private final GLCanvas   mCanvas;
    private final DrawEnv mEnv = new DrawEnv();


    public VideoWindow( Ticker update, DrawUnit... texs ) {
        mOptTicker = update;
        mTexs = texs;

        GLProfile profile = GLProfile.get( GLProfile.GL3 );
        GLCapabilities glc = new GLCapabilities( profile );
        mCanvas = new GLCanvas( glc );

        setSize( 1024, 768 );
        setLocationRelativeTo( null );
        setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        getContentPane().add( mCanvas );

        mCanvas.addGLEventListener( this );
    }


    public void display( GLAutoDrawable gld ) {
        final DrawEnv d = mEnv;
        d.init( gld, null );
        GL gl = d.mGl;

        if( mOptTicker != null ) {
            mOptTicker.tick();
        }

        d.mProj.identity();
        d.mView.identity();
        d.mView.setOrtho( 0, 1, 0, 1, -1, 1 );
        gl.glClearColor( 0, 0, 0, 0 );
        gl.glClear( GL_COLOR_BUFFER_BIT );
        DrawStream s = d.drawStream();
        s.config( true, true, false );
        s.color( 1, 1, 1, 1 );
        gl.glActiveTexture( GL_TEXTURE0 );

        for( int i = 0; i < mTexs.length; i++ ) {
            float y0 = (float)i / mTexs.length;
            float y1 = (float)(i + 1) / mTexs.length;
            mTexs[i].bind( d );

            s.beginQuads();
            s.tex( 0, 1 );
            s.vert( 0, y0 );
            s.tex( 1, 1 );
            s.vert( 1, y0 );
            s.tex( 1, 0 );
            s.vert( 1, y1 );
            s.tex( 0, 0 );
            s.vert( 0, y1 );
            s.end();
        }
    }


    public void init( GLAutoDrawable arg0 ) {}

    @Override
    public void dispose( GLAutoDrawable drawable ) {}


    public void reshape( GLAutoDrawable arg0, int arg1, int arg2, int arg3, int arg4 ) {}


    public void start() {
        setVisible( true );
        new LimitAnimator( mCanvas ).start();
    }

}

