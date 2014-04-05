package bits.drawjav.video;

import java.io.*;
import java.util.Random;

import javax.media.opengl.*;
import javax.swing.JFrame;

import bits.microtime.*;

import com.sun.opengl.util.Animator;

import static javax.media.opengl.GL.*;

/**
* @author decamp
*/
public class TestVideoExportNode {

    public static void main( String[] args ) throws Exception {
        test1();
    }


    static void test1() throws Exception {
        GLCapabilities caps = new GLCapabilities();
        caps.setHardwareAccelerated( true );
        GLCanvas canvas = new GLCanvas( caps );

        JFrame frame = new JFrame();
        frame.setSize( 1024, 768+22 );
        frame.setLocationRelativeTo( null );
        frame.add( canvas );

        canvas.addGLEventListener( new EventHandler() );

        frame.setVisible( true );

        Animator anim = new Animator( canvas );
        anim.start();
    }


    private static final class EventHandler implements GLEventListener {
        private final PlayController mPlayCont  = PlayController.newSteppingInstance( 0, 1001000000 / 30000 );
        private final VideoExportNode mExporter = new VideoExportNode( mPlayCont.clock() );

        private final Random mRand = new Random( 0 );

        private long mStartMicros = 1000000L;
        private long mStopMicros  = 2000000L;


        EventHandler() {
            mPlayCont.control().playStart();
            try {
                mExporter.addVideoWriter( new File( "/tmp/testA.mp4" ), 20, -1, mStartMicros, Long.MAX_VALUE );
                //mExporter.addVideoWriter( new File( "/tmp/testB.mp4" ), -1, 10*1024*1024, 2000000L, 4000000L );
                mExporter.addPngWriter( new File( "/tmp/testC" ),
                                        "cc",
                                        VideoExportNode.PNG_COMPRESSION_BEST_SPEED,
                                        mStartMicros,
                                        mStartMicros + 30000000L );
            } catch( IOException ex ) {
                ex.printStackTrace();
            }
        }


        @Override
        public void init( GLAutoDrawable glad ) {
            mExporter.init( glad );
        }

        @Override
        public void display( GLAutoDrawable glad ) {
            GL gl = glad.getGL();
            mPlayCont.updateClocks();
            long t = mPlayCont.clock().micros();

            if( t >= mStartMicros && t < mStopMicros ) {
                gl.glClearColor( 1f, 1f, 0f, 1f );
            } else if( t >= mStartMicros && t < mStartMicros + ( mStopMicros - mStartMicros ) * 2 ) {
                gl.glClearColor( 1f, 0f, 0f, 1f );
            } else {
                gl.glClearColor( mRand.nextFloat() * 0.25f, mRand.nextFloat() * 0.25f, 0f, 1f );
            }

            gl.glClear( GL_COLOR_BUFFER_BIT );

            gl.glColor3f( 0f, 0f, 0f );
            gl.glBegin( GL_TRIANGLES );
            gl.glVertex2f( 1.0f, 0.5f );
            gl.glVertex2f( 1.0f, 1.0f );
            gl.glVertex2f( 0.5f, 1.0f );
            gl.glEnd();

            mExporter.pushDraw( gl );
            mExporter.popDraw( gl );
        }

        @Override
        public void displayChanged( GLAutoDrawable glad, boolean arg1, boolean arg2 ) {}

        @Override
        public void reshape( GLAutoDrawable glad, int x, int y, int w, int h ) {
            mExporter.reshape( glad, x, y, w, h );
        }

    }

}
