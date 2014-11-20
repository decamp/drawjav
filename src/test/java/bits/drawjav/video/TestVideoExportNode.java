/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.video;

import java.io.*;
import java.util.Random;

import javax.media.opengl.*;
import javax.media.opengl.awt.GLCanvas;
import javax.swing.JFrame;

import bits.draw3d.DrawEnv;
import bits.draw3d.DrawStream;
import bits.glui.util.Animator;
import bits.glui.util.LimitAnimator;
import bits.microtime.*;

import static javax.media.opengl.GL.*;

/**
* @author decamp
*/
public class TestVideoExportNode {

    public static void main( String[] args ) throws Exception {
        test1();
    }


    static void test1() throws Exception {
        GLProfile profile = GLProfile.get( GLProfile.GL3 );
        GLCapabilities caps = new GLCapabilities( profile );
        caps.setHardwareAccelerated( true );
        GLCanvas canvas = new GLCanvas( caps );

        JFrame frame = new JFrame();
        frame.setSize( 1024, 768+22 );
        frame.setLocationRelativeTo( null );
        frame.add( canvas );

        canvas.addGLEventListener( new EventHandler() );

        frame.setVisible( true );

        Animator anim = new LimitAnimator( canvas );
        anim.start();
    }


    private static final class EventHandler implements GLEventListener {
        private final PlayController  mPlayCont = PlayController.createStepping( 0, 1001000000 / 30000 );
        private final VideoExportNode mExporter = new VideoExportNode( mPlayCont.clock() );

        private final Random  mRand = new Random( 0 );
        private final DrawEnv mEnv  = new DrawEnv();

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
            mEnv.init( glad, null );
            mExporter.init( mEnv );
        }

        @Override
        public void dispose( GLAutoDrawable drawable ) {}

        @Override
        public void display( GLAutoDrawable glad ) {
            DrawEnv d = mEnv;
            d.init( glad, null );

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

            DrawStream s = d.drawStream();
            s.config( true, false, false );
            s.color( 0f, 0f, 0f );
            s.beginTris();
            s.vert( 1.0f, 0.5f );
            s.vert( 1.0f, 1.0f );
            s.vert( 0.5f, 1.0f );
            s.end();

            mExporter.pushDraw( d );
            mExporter.popDraw( d );
        }

        @Override
        public void reshape( GLAutoDrawable glad, int x, int y, int w, int h ) {
            mEnv.init( glad, null );
            mExporter.reshape( mEnv );
        }

    }

}
